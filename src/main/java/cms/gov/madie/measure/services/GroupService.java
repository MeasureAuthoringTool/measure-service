package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.exceptions.InvalidDraftStatusException;
import cms.gov.madie.measure.exceptions.InvalidIdException;
import cms.gov.madie.measure.exceptions.LockNotObtainedException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.GroupPopulationUtil;
import cms.gov.madie.measure.utils.MeasureUtil;
import cms.gov.madie.measure.validations.CqlDefinitionReturnTypeService;
import cms.gov.madie.measure.validations.CqlObservationFunctionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureObservation;
import gov.cms.madie.models.measure.MeasureScoring;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.QdmMeasure;
import gov.cms.madie.models.measure.Stratification;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseGroupPopulation;
import gov.cms.madie.models.measure.TestCasePopulationValue;
import gov.cms.madie.models.measure.TestCaseStratificationValue;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class GroupService {

  private final MeasureUtil measureUtil;
  private final MeasureRepository measureRepository;
  private final MeasureService measureService;
  private final CqlDefinitionReturnTypeService cqlDefinitionReturnTypeService;
  private final CqlObservationFunctionService cqlObservationFunctionService;
  private final ModelValidatorFactory modelValidatorFactory;
  private final TestCaseLockService testCaseLockService;
  private final AppConfigService appConfigService;

  public Group createOrUpdateGroup(Group group, String measureId, String username) {

    Measure measure = measureRepository.findById(measureId).orElse(null);
    if (measure == null) {
      throw new ResourceNotFoundException("Measure", measureId);
    }
    if (!measure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(measure.getId());
    }

    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)
        && testCaseLockService.isAnyTestCaseLockedByOthers(measureId, username)) {
      throw new LockNotObtainedException(
          "Unable to update measure.  One or more test cases are locked by another user.");
    }

    if (measure.getModel().equalsIgnoreCase(ModelType.QDM_5_6.getValue())) {
      handleQdmGroupReturnTypes(group, measure);
    } else {
      handleFhirGroupReturnTypes(group, measure);
    }

    if (!CollectionUtils.isEmpty(group.getStratifications())) {
      validateGroupAssociations(measure.getModel(), group);
    }

    // no group present, this is the first group
    if (CollectionUtils.isEmpty(measure.getGroups())) {
      group.setId(ObjectId.get().toString());
      measure.setGroups(List.of(group));
    } else {
      Optional<Group> existingGroupOpt =
          measure.getGroups().stream().filter(g -> g.getId().equals(group.getId())).findFirst();
      // if group already exists, just update it
      if (existingGroupOpt.isPresent()) {
        Group existingGroup = existingGroupOpt.get();
        existingGroup.setScoring(group.getScoring());
        existingGroup.setPopulations(group.getPopulations());
        existingGroup.setMeasureObservations(group.getMeasureObservations());
        existingGroup.setGroupDescription(group.getGroupDescription());
        existingGroup.setImprovementNotation(group.getImprovementNotation());
        existingGroup.setImprovementNotationDescription(group.getImprovementNotationDescription());
        existingGroup.setRateAggregation(group.getRateAggregation());
        existingGroup.setMeasureGroupTypes(group.getMeasureGroupTypes());
        existingGroup.setScoringUnit(group.getScoringUnit());
        existingGroup.setScoringPrecision(group.getScoringPrecision());
        existingGroup.setStratifications(group.getStratifications());
        existingGroup.setPopulationBasis(group.getPopulationBasis());
      } else { // if not present, add into groups collection
        group.setId(ObjectId.get().toString());
        measure.getGroups().add(group);
      }
    }
    updateGroupForTestCases(group, measure.getTestCases(), measure.getModel());

    if (!ModelType.QDM_5_6.getValue().equalsIgnoreCase(measure.getModel())) {
      GroupPopulationUtil.setGroupAndPopulationsDisplayIds(measure, group);
    }

    Measure errors = measureUtil.validateAllMeasureDependencies(measure);
    measure.setErrors(errors.getErrors());
    measure.setCqlErrors(errors.isCqlErrors());

    measure.setLastModifiedBy(username);
    measure.setLastModifiedAt(Instant.now());
    measureRepository.save(measure);
    return group;
  }

  /**
   * Update test case group if there are changes to the measure group as: 1. if
   * population/stratification added to the measure group, add it to the test case group 2. If
   * population/stratification removed from the measure group, add it to the test case group 3. if
   * Observation is removed from the measure group(for Ratio), remove it's all associated
   * observations from test case group 4. If group scoring or population basis changed, remove the
   * group from test case groups
   *
   * @param group Group being changed
   * @param testCases TestCases to iterate over and update
   */
  public void updateGroupForTestCases(Group group, List<TestCase> testCases, String measureModel) {
    if (group != null && !CollectionUtils.isEmpty(testCases)) {
      testCases.forEach(
          testCase -> {
            List<TestCaseGroupPopulation> testCaseGroups = testCase.getGroupPopulations();
            // if test case has groups
            if (!CollectionUtils.isEmpty(testCaseGroups)) {
              TestCaseGroupPopulation testCaseGroupPopulation =
                  testCaseGroups.stream()
                      .filter(
                          testCaseGroup ->
                              StringUtils.equals(group.getId(), testCaseGroup.getGroupId()))
                      .findAny()
                      .orElse(null);
              // if this is not new group
              if (testCaseGroupPopulation != null) {
                // if scoring & population basis not changed, merge the updates
                if (StringUtils.equals(testCaseGroupPopulation.getScoring(), group.getScoring())
                    && StringUtils.equals(
                        testCaseGroupPopulation.getPopulationBasis(), group.getPopulationBasis())) {
                  updateTestCaseGroupWithMeasureGroup(testCaseGroupPopulation, group, measureModel);
                } else {
                  removeGroupFromTestCase(group.getId(), testCase);
                }
              }
            }
          });
    }
  }

  public Measure deleteMeasureGroup(String measureId, String groupId, String username) {

    if (measureId == null || measureId.trim().isEmpty()) {
      throw new InvalidIdException("Measure Id cannot be null");
    }
    //    Measure measure = measureRepository.findById(measureId).orElse(null);
    Measure measure = measureService.findMeasureById(measureId);
    if (measure == null) {
      throw new ResourceNotFoundException("Measure", measureId);
    }

    if (!measure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(measure.getId());
    }

    measureService.verifyAuthorization(username, measure);

    if (groupId == null || groupId.trim().isEmpty()) {
      throw new InvalidIdException("Measure group Id cannot be null");
    }

    List<Group> remainingGroups =
        measure.getGroups().stream().filter(g -> !g.getId().equals(groupId)).toList();

    // to check if given group id is present
    if (remainingGroups.size() == measure.getGroups().size()) {
      throw new ResourceNotFoundException("Group", groupId);
    }

    measure.setGroups(remainingGroups);
    if (!CollectionUtils.isEmpty(remainingGroups)) {
      remainingGroups.stream()
          .forEach(
              group -> {
                GroupPopulationUtil.setGroupAndPopulationsDisplayIds(measure, group);
              });
    }
    log.info(
        "User [{}] has successfully deleted a group with Id [{}] from measure [{}]",
        username,
        groupId,
        measure.getId());
    List<TestCase> testCases = measure.getTestCases();
    removeGroupFromTestCases(groupId, testCases);
    return measureRepository.save(measure);
  }

  public void updateTestCaseGroupWithMeasureGroup(
      TestCaseGroupPopulation testCaseGroup, Group measureGroup, String measureModel) {
    // update test case populations based on measure group population
    List<TestCasePopulationValue> populations =
        measureGroup.getPopulations().stream()
            .map(measurePopulation -> updateTestCasePopulation(measurePopulation, testCaseGroup))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    // retain observations for CV group if there are any
    if (StringUtils.equals(
        measureGroup.getScoring(), MeasureScoring.CONTINUOUS_VARIABLE.toString())) {
      populations.addAll(findTestCaseObservations(testCaseGroup));
    } else if (StringUtils.equals(measureGroup.getScoring(), MeasureScoring.RATIO.toString())
        && !CollectionUtils.isEmpty(measureGroup.getMeasureObservations())) {
      //  retain all observations if both denominator and numerator observations present
      if (measureGroup.getMeasureObservations().size() == 2) {
        populations.addAll(findTestCaseObservations(testCaseGroup));
      } else {
        MeasureObservation observation = measureGroup.getMeasureObservations().get(0);
        Population population =
            findMeasurePopulation(observation.getCriteriaReference(), measureGroup);
        List<TestCasePopulationValue> observations;
        if (population.getName() == PopulationType.DENOMINATOR) {
          observations =
              findTestCasePopulationsContainingId(testCaseGroup, "denominatorObservation");
        } else {
          observations = findTestCasePopulationsContainingId(testCaseGroup, "numeratorObservation");
        }
        populations.addAll(observations);
      }
    }
    testCaseGroup.setPopulationValues(populations);
    // update test case stratification based on measure group stratification
    if (CollectionUtils.isEmpty(measureGroup.getStratifications())) {
      testCaseGroup.setStratificationValues(List.of());
    } else {
      AtomicInteger count = new AtomicInteger(1);
      List<TestCaseStratificationValue> stratification =
          measureGroup.getStratifications().stream()
              .map(
                  measureStrata -> {
                    if (null != measureStrata.getAssociation()) {
                      String strataName =
                          String.format(
                              "Strata-%d %s",
                              count.getAndIncrement(), measureStrata.getAssociation().getDisplay());
                      return updateTestCaseStratification(
                          measureStrata, testCaseGroup, strataName, measureModel);
                    } else {
                      String strataName = String.format("Strata-%d", count.getAndIncrement());
                      return updateTestCaseStratification(
                          measureStrata, testCaseGroup, strataName, measureModel);
                    }
                  })
              .filter(Objects::nonNull)
              .toList();
      testCaseGroup.setStratificationValues(stratification);
    }
  }

  public void removeGroupFromTestCases(String groupId, List<TestCase> testCases) {
    if (StringUtils.isNotBlank(groupId) && !CollectionUtils.isEmpty(testCases)) {
      testCases.forEach(testCase -> removeGroupFromTestCase(groupId, testCase));
    }
  }

  public void validateGroupAssociations(String model, Group group) {
    ModelValidator validator =
        modelValidatorFactory.getModelValidator(ModelType.valueOfName(model));
    validator.validateGroupAssociations(group);
  }

  private TestCasePopulationValue updateTestCasePopulation(
      Population population, TestCaseGroupPopulation testCaseGroup) {
    // if no cql definition(optional pop or unselected), no need to consider population
    if (StringUtils.isEmpty(population.getDefinition())) {
      return null;
    }
    // find matching population
    TestCasePopulationValue testCasePopulation =
        findTestCasePopulation(population.getId(), testCaseGroup);
    // if population did not find, create new one
    if (testCasePopulation == null) {
      testCasePopulation =
          TestCasePopulationValue.builder()
              .id(population.getId())
              .expected(null)
              .actual(null)
              .name(population.getName())
              .build();
    }
    return testCasePopulation;
  }

  protected TestCaseStratificationValue updateTestCaseStratification(
      Stratification stratification,
      TestCaseGroupPopulation testCaseGroup,
      String strataName,
      String measureModel) {
    // if no cql definition(optional), no need to consider stratification
    if (StringUtils.isEmpty(stratification.getCqlDefinition())) {
      return null;
    }

    TestCaseStratificationValue testCaseStrata = null;
    if (!CollectionUtils.isEmpty(testCaseGroup.getStratificationValues())) {
      // find matching strata
      testCaseStrata =
          testCaseGroup.getStratificationValues().stream()
              .filter(testStrata -> StringUtils.equals(testStrata.getId(), stratification.getId()))
              .findAny()
              .orElse(null);
    }
    // if population did not find, create new one
    if (testCaseStrata == null) {
      testCaseStrata =
          TestCaseStratificationValue.builder()
              .id(stratification.getId())
              .expected(null)
              .actual(null)
              .build();
    }
    testCaseStrata.setName(strataName);
    handlePopulationChange(testCaseStrata, testCaseGroup, measureModel, stratification);

    return testCaseStrata;
  }

  private void handlePopulationChange(
      TestCaseStratificationValue testCaseStrata,
      TestCaseGroupPopulation testCaseGroup,
      String measureModel,
      Stratification stratification) {
    List<TestCasePopulationValue> testCasePopulationValues = testCaseStrata.getPopulationValues();
    List<TestCasePopulationValue> testCasePopulationValuesFromGroup =
        testCaseGroup.getPopulationValues();

    if (!CollectionUtils.isEmpty(testCasePopulationValuesFromGroup)) {
      if (!CollectionUtils.isEmpty(testCasePopulationValues)) {
        for (TestCasePopulationValue testCasePopulationValueFromGroup :
            testCasePopulationValuesFromGroup) {
          // if there is new population value from testCasePopulationValuesFromGroup
          if (!findExistsTestCasePopulationValue(
              testCasePopulationValueFromGroup.getId(), testCasePopulationValues)) {
            testCasePopulationValues.add(testCasePopulationValueFromGroup);
          }
          // delete any that is not in testCasePopulationValuesFromGroup
          List<TestCasePopulationValue> tempTestCasePopulationValues = new ArrayList<>();
          for (TestCasePopulationValue tempTestCasePopulationValue : testCasePopulationValues) {
            if (StringUtils.equals(measureModel, ModelType.QDM_5_6.getValue())) {
              if (findExistsTestCasePopulationValue(
                  tempTestCasePopulationValue.getId(), testCasePopulationValuesFromGroup)) {
                tempTestCasePopulationValues.add(tempTestCasePopulationValue);
              }
            } else {
              // remove association from testcases when stratifications are changed in measure
              // groups
              if (handleStratificationAssociationChange(
                  tempTestCasePopulationValue, stratification)) {
                tempTestCasePopulationValues.add(tempTestCasePopulationValue);
              }
            }
          }
          testCaseStrata.setPopulationValues(tempTestCasePopulationValues);
        }
      } // when there is new strat
      else {
        testCaseStrata.setPopulationValues(testCasePopulationValuesFromGroup);
      }
    }
  }

  private boolean handleStratificationAssociationChange(
      TestCasePopulationValue tempTestCasePopulationValue, Stratification stratification) {
    if (stratification.getAssociations() != null) {
      PopulationType populationAsocciation =
          stratification.getAssociations().stream()
              .filter(
                  association -> {
                    return association
                        .getDisplay()
                        .equalsIgnoreCase(tempTestCasePopulationValue.getName().getDisplay());
                  })
              .findFirst()
              .orElse(null);

      if (populationAsocciation != null) {
        return true;
      }
    }
    return false;
  }

  private boolean findExistsTestCasePopulationValue(
      String id, List<TestCasePopulationValue> testCasePopulationValues) {
    return testCasePopulationValues.stream()
        .anyMatch(testCasePopulationValue -> id.equalsIgnoreCase(testCasePopulationValue.getId()));
  }

  private TestCasePopulationValue findTestCasePopulation(
      String populationId, TestCaseGroupPopulation testCaseGroup) {
    return testCaseGroup.getPopulationValues().stream()
        .filter(testPopulation -> StringUtils.equals(testPopulation.getId(), populationId))
        .findAny()
        .orElse(null);
  }

  private Population findMeasurePopulation(String populationId, Group group) {
    return group.getPopulations().stream()
        .filter(testPopulation -> StringUtils.equals(testPopulation.getId(), populationId))
        .findAny()
        .orElse(null);
  }

  private List<TestCasePopulationValue> findTestCaseObservations(
      TestCaseGroupPopulation testCaseGroup) {
    return testCaseGroup.getPopulationValues().stream()
        .filter(
            p ->
                p.getName() == PopulationType.MEASURE_OBSERVATION
                    || p.getName() == PopulationType.MEASURE_POPULATION_OBSERVATION
                    || p.getName() == PopulationType.DENOMINATOR_OBSERVATION
                    || p.getName() == PopulationType.NUMERATOR_OBSERVATION)
        .collect(Collectors.toList());
  }

  private List<TestCasePopulationValue> findTestCasePopulationsContainingId(
      TestCaseGroupPopulation testCaseGroup, String id) {
    return testCaseGroup.getPopulationValues().stream()
        .filter(p -> StringUtils.contains(p.getId(), id))
        .collect(Collectors.toList());
  }

  private void removeGroupFromTestCase(String groupId, TestCase testCase) {
    if (testCase.getGroupPopulations() != null) {
      List<TestCaseGroupPopulation> remainingGroups =
          testCase.getGroupPopulations().stream()
              .filter(group -> !groupId.equals(group.getGroupId()))
              .toList();
      testCase.setGroupPopulations(remainingGroups);
    }
  }

  protected void handleFhirGroupReturnTypes(Group group, Measure measure) {
    try {
      cqlDefinitionReturnTypeService.validateCqlDefinitionReturnTypes(group, measure.getElmJson());
      cqlObservationFunctionService.validateObservationFunctions(group, measure.getElmJson());
    } catch (JsonProcessingException ex) {
      log.error(
          "An error occurred while validating population "
              + "definition return types for FHIR measure {}",
          measure.getId(),
          ex);
      throw new IllegalArgumentException("Invalid elm json");
    }
  }

  protected void handleQdmGroupReturnTypes(Group group, Measure measure) {
    QdmMeasure qdmMeasure = (QdmMeasure) measure;

    try {
      cqlDefinitionReturnTypeService.validateCqlDefinitionReturnTypesForQdm(
          group, measure.getElmJson(), qdmMeasure.isPatientBasis());
    } catch (JsonProcessingException ex) {
      log.error(
          "An error occurred while validating population "
              + "definition return types for QDM measure {}",
          measure.getId(),
          ex);
      throw new IllegalArgumentException("Invalid elm json");
    }
  }

  public Stratification createOrUpdateStratification(
      String groupId, String measureId, Stratification stratification, String username) {
    Measure measure = measureRepository.findById(measureId).orElse(null);
    if (measure == null) {
      throw new ResourceNotFoundException("Measure", measureId);
    }
    if (!measure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(measure.getId());
    }

    measureService.verifyAuthorization(username, measure);

    Optional<Group> group =
        measure.getGroups().stream().filter(g -> g.getId().equals(groupId)).findFirst();

    // no stratification present, this is the first stratification
    if (group.isPresent() && CollectionUtils.isEmpty(group.get().getStratifications())) {
      stratification.setId(ObjectId.get().toString());
      group.get().setStratifications(List.of(stratification));
    } else {
      Optional<Stratification> existingStratificationOpt =
          group.get().getStratifications().stream()
              .filter(s -> s.getId().equals(stratification.getId()))
              .findFirst();
      // if stratification already exists, just update it
      if (existingStratificationOpt.isPresent()) {
        Stratification existingStratification = existingStratificationOpt.get();
        existingStratification.setCqlDefinition(stratification.getCqlDefinition());
        existingStratification.setDescription(stratification.getDescription());
        existingStratification.setAssociations(stratification.getAssociations());
        existingStratification.setAssociation(stratification.getAssociation());
      } else { // if not present, add into stratification collection
        stratification.setId(ObjectId.get().toString());
        List<Stratification> newStratList = new ArrayList<>();
        newStratList.addAll(group.get().getStratifications());
        newStratList.add(stratification);
        group.get().setStratifications(newStratList);
      }
    }

    Measure errors = measureUtil.validateAllMeasureDependencies(measure);
    measure.setErrors(errors.getErrors());
    measure.setCqlErrors(errors.isCqlErrors());

    measure.setLastModifiedBy(username);
    measure.setLastModifiedAt(Instant.now());
    measureRepository.save(measure);
    return stratification;
  }

  public Measure deleteStratification(
      String measureId, String groupId, String stratificationId, String username) {

    if (measureId == null || measureId.trim().isEmpty()) {
      throw new InvalidIdException("Measure Id cannot be null");
    }
    Measure measure = measureService.findMeasureById(measureId);
    if (measure == null) {
      throw new ResourceNotFoundException("Measure", measureId);
    }

    if (!measure.getMeasureMetaData().isDraft()) {
      throw new InvalidDraftStatusException(measure.getId());
    }

    measureService.verifyAuthorization(username, measure);

    if (groupId == null || groupId.trim().isEmpty()) {
      throw new InvalidIdException("Measure group Id cannot be null");
    }

    Optional<Group> group =
        measure.getGroups().stream().filter(g -> g.getId().equals(groupId)).findFirst();

    // to check if given group id is present
    if (!group.isPresent()) {
      throw new ResourceNotFoundException("Group", groupId);
    }

    List<Stratification> remainingStratifications =
        group.get().getStratifications().stream()
            .filter(s -> !s.getId().equals(stratificationId))
            .toList();

    if (remainingStratifications.size() == group.get().getStratifications().size()) {
      throw new ResourceNotFoundException("Stratification", stratificationId);
    }

    group.get().setStratifications(remainingStratifications);
    log.info(
        "User [{}] has successfully deleted a group with Id [{}] from measure [{}]",
        username,
        groupId,
        measure.getId());
    return measureRepository.save(measure);
  }
}
