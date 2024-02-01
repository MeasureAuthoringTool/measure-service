package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.repositories.OrganizationRepository;
import cms.gov.madie.measure.services.MeasureSetService;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.measure.ElmJson;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import cms.gov.madie.measure.exceptions.CqlElmTranslationErrorException;
import cms.gov.madie.measure.exceptions.CqlElmTranslationServiceException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.ElmTranslatorClient;
import cms.gov.madie.measure.services.MeasureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/measure-transfer")
public class MeasureTransferController {
  private static final String HARP_ID_HEADER = "harp-id";
  private final MeasureRepository repository;
  private final MeasureService measureService;
  private final ActionLogService actionLogService;
  private final MeasureSetService measureSetService;
  private final ElmTranslatorClient elmTranslatorClient;
  private final OrganizationRepository organizationRepository;

  @PostMapping("/mat-measures")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<Measure> createMeasure(
      HttpServletRequest request,
      @RequestBody @Validated({Measure.ValidationSequence.class}) Measure measure,
      @Value("${lambda-api-key}") String apiKey) {
    String harpId = request.getHeader(HARP_ID_HEADER);
    log.info(
        "Measure [{}] is being transferred over to MADiE by [{}]",
        measure.getMeasureName(),
        harpId);
    measureService.checkDuplicateCqlLibraryName(measure.getCqlLibraryName());

    setMeasureElmJsonAndErrors(measure, apiKey, harpId);

    // TODO: decide on audit records
    Instant now = Instant.now();
    measure.setCreatedAt(now);
    measure.setLastModifiedAt(now);
    if (measure.getMeasureMetaData() != null) {
      measure.getMeasureMetaData().setDraft(true);
      updateStewardAndDevelopers(measure);
    } else {
      MeasureMetaData metaData = new MeasureMetaData();
      metaData.setDraft(true);
      measure.setMeasureMetaData(metaData);
      updateStewardAndDevelopers(measure);
    }
    // set ids for groups
    measure.getGroups().forEach(group -> group.setId(ObjectId.get().toString()));
    measure.setGroups(reorderGroupPopulations(measure.getGroups()));
    Measure savedMeasure = repository.save(measure);
    measureSetService.createMeasureSet(
        harpId, savedMeasure.getId(), savedMeasure.getMeasureSetId());
    log.info("Measure [{}] transfer complete", measure.getMeasureName());
    actionLogService.logAction(
        savedMeasure.getId(), Measure.class, ActionType.IMPORTED, savedMeasure.getCreatedBy());
    return ResponseEntity.status(HttpStatus.CREATED).body(savedMeasure);
  }

  private void updateStewardAndDevelopers(Measure measure) {
    List<Organization> organizationList = organizationRepository.findAll();
    if (CollectionUtils.isEmpty(organizationList)) {
      log.debug(
          "No organizations are available while transferring MAT measure : [{}]",
          measure.getMeasureName());
      throw new RuntimeException("No organizations are available");
    }
    updateOrganizationName(measure);
    Organization steward = measure.getMeasureMetaData().getSteward();
    if (steward != null) {
      Optional<Organization> stewardOrg =
          organizationList.stream()
              .filter(org -> org.getName().equalsIgnoreCase(steward.getName()))
              .findFirst();
      measure.getMeasureMetaData().setSteward(stewardOrg.orElse(null));
    }

    List<Organization> developersList = measure.getMeasureMetaData().getDevelopers();
    if (developersList != null && !developersList.isEmpty()) {
      List<Organization> developersOrgs =
          organizationList.stream()
              .filter(
                  org ->
                      developersList.stream()
                          .anyMatch(
                              developer -> developer.getName().equalsIgnoreCase(org.getName())))
              .toList();
      measure.getMeasureMetaData().setDevelopers(developersOrgs);
    }
  }

  /**
   * @param measure mat measure There has been few updates to org names in MADiE when compared to
   *     orgs in MAT. updateOrganizationName will update the steward & developers names, so that
   *     they can match with updated org names in MADiE
   */
  private void updateOrganizationName(Measure measure) {
    Map<String, String> modifiedOrganizations = new HashMap<>();
    modifiedOrganizations.put(
        "American College of Cardiology - ACCF/AHA Task Force on Performance Measures",
        "American College of Cardiology - ACC/AHA Task Force on Performance Measures");
    modifiedOrganizations.put(
        "Arizona State University - Dept of Biomedical Infomatics",
        "Arizona State University - Dept of Biomedical Informatics");
    modifiedOrganizations.put("CancerLin Q", "CancerLinQ");
    modifiedOrganizations.put("Innovaccer Anylytics", "Innovaccer");
    modifiedOrganizations.put("Intermoutain Healthcare", "Intermountain Healthcare");

    Organization steward = measure.getMeasureMetaData().getSteward();
    if (steward != null && modifiedOrganizations.containsKey(steward.getName())) {
      measure
          .getMeasureMetaData()
          .getSteward()
          .setName(modifiedOrganizations.get(steward.getName()));
    }

    List<Organization> developersList = measure.getMeasureMetaData().getDevelopers();
    if (developersList != null && !developersList.isEmpty()) {
      measure
          .getMeasureMetaData()
          .getDevelopers()
          .forEach(
              d -> {
                if (modifiedOrganizations.containsKey(d.getName())) {
                  d.setName(modifiedOrganizations.get(d.getName()));
                }
              });
    }
  }

  private void setMeasureElmJsonAndErrors(Measure measure, String apiKey, String harpId) {
    try {
      final ElmJson elmJson =
          elmTranslatorClient.getElmJsonForMatMeasure(measure.getCql(), apiKey, harpId);
      if (elmTranslatorClient.hasErrors(elmJson)) {
        measure.setCqlErrors(true);
      }
      measure.setElmJson(elmJson.getJson());
      measure.setElmXml(elmJson.getXml());
    } catch (CqlElmTranslationServiceException | CqlElmTranslationErrorException e) {
      log.error(
          "CqlElmTranslationServiceException for transferred measure {} ",
          measure.getMeasureName(),
          e);
      measure.setCqlErrors(true);
    } catch (Exception ex) {
      log.error(
          "An error occurred while getting ELM Json for transferred measure {}",
          measure.getMeasureName(),
          ex);
      measure.setCqlErrors(true);
    }
  }

  protected List<Group> reorderGroupPopulations(List<Group> groups) {
    List<Population> newPopulations;
    List<Group> newGroups = new ArrayList<>();
    if (!CollectionUtils.isEmpty(groups)) {
      for (Group group : groups) {
        newPopulations = new ArrayList<>();
        List<Population> populations = group.getPopulations();
        if (!CollectionUtils.isEmpty(populations)) {
          newPopulations.add(findPopulation(populations, PopulationType.INITIAL_POPULATION));
          newPopulations.add(findPopulation(populations, PopulationType.DENOMINATOR));
          newPopulations.add(findPopulation(populations, PopulationType.DENOMINATOR_EXCLUSION));
          newPopulations.add(findPopulation(populations, PopulationType.NUMERATOR));
          newPopulations.add(findPopulation(populations, PopulationType.NUMERATOR_EXCLUSION));
          newPopulations.add(findPopulation(populations, PopulationType.DENOMINATOR_EXCEPTION));
          group.setPopulations(newPopulations);
          newGroups.add(group);
        }
      }
    }
    return newGroups;
  }

  private Population findPopulation(List<Population> populations, PopulationType populationType) {
    return populations.stream()
        .filter(population -> population.getName() == populationType)
        .findFirst()
        .orElse(
            Population.builder()
                .id(ObjectId.get().toString())
                .name(populationType)
                .definition("")
                .build());
  }
}
