package cms.gov.madie.measure.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.utils.ResourceUtil;
import gov.cms.madie.models.common.*;
import cms.gov.madie.measure.dto.MeasureListDTO;
import gov.cms.madie.models.measure.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssociateCmsIdServiceTest implements ResourceUtil {
  @Mock private MeasureRepository measureRepository;
  @Mock private MeasureSetRepository measureSetRepository;
  @Mock private MeasureSetService measureSetService;
  @Mock private AppConfigService appConfigService;
  @Mock private MeasureLockService measureLockService;
  @Mock private ActionLogService actionLogService;

  @Spy @InjectMocks private AssociateCmsIdService associateCmsIdService;

  private MeasureMetaData draftMeasureMetaData;
  private MeasureMetaData finalMeasureMetaData;
  private String elmJson;
  private Measure measure1;
  private Measure measure2;
  private MeasureListDTO measureList;

  @BeforeEach
  public void setUp() {
    Stratification strat1 = new Stratification();
    strat1.setId("strat-1");
    strat1.setCqlDefinition("Initial Population");

    Stratification emptyStrat = new Stratification();

    List<Reference> references =
        List.of(
            Reference.builder()
                .id("test reference id")
                .referenceText("test reference text")
                .referenceType("DOCUMENT")
                .build());
    List<Endorsement> endorsements =
        List.of(
            Endorsement.builder()
                .endorserSystemId("test endorsement system id")
                .endorser("NQF")
                .endorsementId("testEndorsementId")
                .build());
    List<MeasureDefinition> definitions =
        List.of(
            MeasureDefinition.builder()
                .id("test definition id")
                .term("test term")
                .definition("test definition")
                .build());

    List<Organization> developersList = new ArrayList<>();
    developersList.add(Organization.builder().name("SB 2").build());
    developersList.add(Organization.builder().name("SB 3").build());

    draftMeasureMetaData =
        MeasureMetaData.builder()
            .steward(Organization.builder().name("SB").build())
            .developers(developersList)
            .copyright("Copyright@SB")
            .references(references)
            .draft(true)
            .endorsements(endorsements)
            .definition("test definition")
            .experimental(false)
            .transmissionFormat("test transmission format")
            .measureDefinitions(definitions)
            .build();

    finalMeasureMetaData =
        MeasureMetaData.builder()
            .steward(Organization.builder().name("SB").build())
            .developers(developersList)
            .copyright("Copyright@SB")
            .references(references)
            .draft(false)
            .endorsements(endorsements)
            .definition("test definition")
            .experimental(false)
            .transmissionFormat("test transmission format")
            .build();

    // Present in DB and has ID
    Group group2 =
        Group.builder()
            .id("xyz-p12r-12ert")
            .populationBasis("Encounter")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "FactorialOfFive",
                        null,
                        null,
                        "IntialPopulation_1")))
            .stratifications(List.of(strat1, emptyStrat))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    List<Group> groups = new ArrayList<>();
    groups.add(group2);
    elmJson = getData("/test_elm.json");
    measure1 =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .model(ModelType.QI_CORE.getValue())
            .cql("test cql")
            .elmJson(elmJson)
            .measureSetId("IDIDID")
            .cqlLibraryName("MSR01Library")
            .measureName("MSR01")
            .measureMetaData(draftMeasureMetaData)
            .version(new Version(0, 0, 1))
            .groups(groups)
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .build();

    measure2 =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .cql("test cql")
            .model(ModelType.QDM_5_6.getValue())
            .elmJson(elmJson)
            .measureSetId("2D2D2D")
            .measureName("MSR02")
            .version(new Version(0, 0, 1))
            .groups(groups)
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .measureMetaData(draftMeasureMetaData)
            .build();

    measureList =
        MeasureListDTO.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .model(ModelType.QI_CORE.getValue())
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .measureMetaData(draftMeasureMetaData)
            .version(new Version(0, 0, 1))
            .build();
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenMeasuresWithGivenIdNotFound() {
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            associateCmsIdService.associateCmsId(
                "OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenUserIsNotOwnerOfTheMeasures() {
    MeasureSet measureSet = MeasureSet.builder().owner("owner").build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        UnauthorizedException.class,
        () ->
            associateCmsIdService.associateCmsId(
                "newowner", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenBothTheMeasureAreQICore() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        InvalidRequestException.class,
        () ->
            associateCmsIdService.associateCmsId(
                "OWNER", "qiCoreMeasureId", "qiCoreMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenBothTheMeasureAreQDM() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        InvalidRequestException.class,
        () -> associateCmsIdService.associateCmsId("OWNER", "qdmMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenQDMMeasureHasNoCmsId() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        InvalidRequestException.class,
        () ->
            associateCmsIdService.associateCmsId(
                "OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenQICoreMeasureHasCmsId() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () ->
            associateCmsIdService.associateCmsId(
                "OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenQICoreMeasureIsVersioned() {
    measure1.setMeasureMetaData(finalMeasureMetaData);
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId("IDIDID")).thenReturn(qiCoreMeasureSet);
    when(measureSetService.findByMeasureSetId("2D2D2D")).thenReturn(qdmMeasureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () ->
            associateCmsIdService.associateCmsId(
                "OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenAnyQICoreMeasureHasSameCmsId() {
    Measure qiCoreMeasure =
        Measure.builder()
            .model(ModelType.QI_CORE.getValue())
            .measureSetId("NewIDIDID")
            .measureMetaData(draftMeasureMetaData)
            .build();
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId("IDIDID")).thenReturn(qiCoreMeasureSet);
    when(measureSetService.findByMeasureSetId("2D2D2D")).thenReturn(qdmMeasureSet);
    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(List.of(qiCoreMeasure));

    assertThrows(
        InvalidResourceStateException.class,
        () ->
            associateCmsIdService.associateCmsId(
                "OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdSuccessfullyWithoutCpyingMetaData() {

    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet updatedQiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").cmsId(12).owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId("IDIDID")).thenReturn(qiCoreMeasureSet);
    when(measureSetService.findByMeasureSetId("2D2D2D")).thenReturn(qdmMeasureSet);

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(List.of());
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedQiCoreMeasureSet);

    MeasureSet updatedMeasureSet =
        associateCmsIdService.associateCmsId("OWNER", "qiCoreMeasureId", "qdmMeasureId", false);
    assertThat(updatedMeasureSet.getOwner(), is(equalTo(updatedQiCoreMeasureSet.getOwner())));
    assertThat(
        updatedMeasureSet.getMeasureSetId(),
        is(equalTo(updatedQiCoreMeasureSet.getMeasureSetId())));
    assertThat(updatedMeasureSet.getCmsId(), is(equalTo(updatedQiCoreMeasureSet.getCmsId())));
  }

  @Test
  public void testAssociateCmsIdSuccessfullyWithCpyingMetaData() {

    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet updatedQiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").cmsId(12).owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId("IDIDID")).thenReturn(qiCoreMeasureSet);
    when(measureSetService.findByMeasureSetId("2D2D2D")).thenReturn(qdmMeasureSet);

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(List.of());
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedQiCoreMeasureSet);

    MeasureSet updatedMeasureSet =
        associateCmsIdService.associateCmsId("OWNER", "qiCoreMeasureId", "qdmMeasureId", true);
    assertThat(updatedMeasureSet.getOwner(), is(equalTo(updatedQiCoreMeasureSet.getOwner())));
    assertThat(
        updatedMeasureSet.getMeasureSetId(),
        is(equalTo(updatedQiCoreMeasureSet.getMeasureSetId())));
    assertThat(updatedMeasureSet.getCmsId(), is(equalTo(updatedQiCoreMeasureSet.getCmsId())));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionForNullQiCoreMeasure() {
    assertThrows(
        ResourceNotFoundException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", null, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionForNullQdmMeasure() {
    assertThrows(
        ResourceNotFoundException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, null));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenBothTheMeasureAreQiCore411() {
    assertThrows(
        InvalidRequestException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure1));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenBothTheMeasureAreQiCore600() {
    measureList.setModel(ModelType.QI_CORE_6_0_0.getValue());
    assertThrows(
        InvalidRequestException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure1));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenBothTheMeasureAreQDM() {
    assertThrows(
        InvalidRequestException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure1));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenUsernameIsNotOwner() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    measure1.setMeasureSet(measureSet);
    measure2.setMeasureSet(measureSet);

    assertThrows(
        UnauthorizedException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("NOT_OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenQDMMeasureHasNoCmsId() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    measure1.setMeasureSet(measureSet);
    measure2.setMeasureSet(measureSet);

    assertThrows(
        InvalidRequestException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenQICoreMeasureHasCmsId() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").cmsId(12).build();
    measure1.setMeasureSet(measureSet);
    measure2.setMeasureSet(measureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenQICoreMeasureIsComposite() {
    measure1.setMeasureMetaData(draftMeasureMetaData.toBuilder().composite(true).build());
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    measure1.setMeasureSet(qiCoreMeasureSet);
    measure2.setMeasureSet(qdmMeasureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenQICoreMeasureIsVersioned() {
    measure1.setMeasureMetaData(finalMeasureMetaData);
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    measure1.setMeasureSet(qiCoreMeasureSet);
    measure2.setMeasureSet(qdmMeasureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenAnyQICoreMeasureHasSameCmsId() {
    Measure qiCoreMeasure =
        Measure.builder()
            .model(ModelType.QI_CORE.getValue())
            .measureSetId("NewIDIDID")
            .measureMetaData(draftMeasureMetaData)
            .build();
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(List.of(qiCoreMeasure));

    measure1.setMeasureSet(qiCoreMeasureSet);
    measure2.setMeasureSet(qdmMeasureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociation() {
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(Collections.emptyList());

    measure1.setMeasureSet(qiCoreMeasureSet);
    measure2.setMeasureSet(qdmMeasureSet);

    when(measureLockService.checkMeasureLock(anyString(), any(Measure.class), anyString()))
        .thenReturn(false);

    assertDoesNotThrow(
        () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationWhenMeasureIsLocked() {
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(Collections.emptyList());

    measure1.setMeasureSet(qiCoreMeasureSet);
    measure2.setMeasureSet(qdmMeasureSet);

    when(measureLockService.checkMeasureLock(anyString(), any(Measure.class), anyString()))
        .thenThrow(
            new LockNotObtainedException(
                "Unable to associate measure. Locked while being edited by another.user"));

    Exception exception =
        assertThrows(
            LockNotObtainedException.class,
            () -> associateCmsIdService.validateCmsIdAssociation("OWNER", measure1, measure2));

    assertThat(
        exception.getMessage(),
        is(equalTo("Unable to associate measure. Locked while being edited by another.user")));
  }
}
