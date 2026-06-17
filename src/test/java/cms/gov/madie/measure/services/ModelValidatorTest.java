package cms.gov.madie.measure.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.factories.ModelValidatorFactory;
import gov.cms.madie.models.measure.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import cms.gov.madie.measure.exceptions.InvalidGroupException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Organization;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class ModelValidatorTest {
  @Autowired private ModelValidatorFactory modelValidatorFactory;

  @Test
  void createModelValidatorTest() {
    assertNotNull(modelValidatorFactory);
  }

  @Test
  void createQdmModelValidatorTest() {
    assertNotNull(modelValidatorFactory);

    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QDM_5_6);
    assertTrue(validator instanceof QdmModelValidator);
  }

  @Test
  void useQdmModelValidatorTest() {
    assertNotNull(modelValidatorFactory);
    Group group = Group.builder().stratifications(new ArrayList<Stratification>()).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QDM_5_6);
    assertTrue(validator instanceof QdmModelValidator);
    try {
      validator.validateGroupAssociations(group);
    } catch (Exception e) {
      fail(e);
    }
  }

  @Test
  void useQdmModelValidatorTestHasInvalidAssociation() {
    assertNotNull(modelValidatorFactory);
    Stratification strat = new Stratification();
    List<Stratification> strats = new ArrayList<>();
    strat.setCqlDefinition("Initial Population");
    strats.add(strat);

    Group group = Group.builder().stratifications(strats).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QDM_5_6);
    assertTrue(validator instanceof QdmModelValidator);
    try {
      validator.validateGroupAssociations(group);
    } catch (Exception e) {
      assertEquals("QDM group stratifications cannot be associated.", e.getMessage());
    }
  }

  @Test
  void useQdmModelValidatorTestHasValidAssociation() {
    assertNotNull(modelValidatorFactory);
    Stratification strat = new Stratification();
    List<Stratification> strats = new ArrayList<>();

    strats.add(strat);

    Group group = Group.builder().stratifications(strats).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QDM_5_6);
    assertTrue(validator instanceof QdmModelValidator);
    try {
      validator.validateGroupAssociations(group);
    } catch (Exception e) {
      fail(e);
    }
  }

  @Test
  void useQdmModelValidatorTestMeasureHasNoGroup() {
    assertNotNull(modelValidatorFactory);
    Measure measure = Measure.builder().id("1").groups(List.of()).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QDM_5_6);
    assertTrue(validator instanceof QdmModelValidator);
    try {
      validator.validateGroups(measure);
      fail("Should fail because, there should be at least one population criteria");
    } catch (InvalidResourceStateException e) {
      assertEquals(
          "Response could not be completed for Measure with ID 1, since there is no population criteria on the measure.",
          e.getMessage());
    }
  }

  @Test
  void createQicoreModelValidatorTest() {
    assertNotNull(modelValidatorFactory);

    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
  }

  @Test
  void useQicoreModelValidatorTestNoStratifications() {
    assertNotNull(modelValidatorFactory);
    Group group =
        Group.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .stratifications(new ArrayList<Stratification>())
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    try {
      validator.validateGroupAssociations(group);

    } catch (Exception e) {
      fail(e);
    }
  }

  @Test
  void useQicoreModelValidatorTestHasInvalidAssociation() {
    assertNotNull(modelValidatorFactory);
    List<Stratification> strats = List.of(Stratification.builder().cqlDefinition("test").build());

    Group group =
        Group.builder().scoring(MeasureScoring.COHORT.toString()).stratifications(strats).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    try {
      validator.validateGroupAssociations(group);
      // Should no longer throw error
    } catch (InvalidGroupException e) {
      fail("Should not fail because QICore strat association can be null");
    }
  }

  @Test
  void useQicoreModelValidatorTestHasAssociation() {
    assertNotNull(modelValidatorFactory);
    Stratification strat = new Stratification();
    List<Stratification> strats = new ArrayList<>();
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    strats.add(strat);

    Group group =
        Group.builder().scoring(MeasureScoring.COHORT.toString()).stratifications(strats).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    try {
      validator.validateGroupAssociations(group);

    } catch (Exception e) {
      fail(e);
    }
  }

  @Test
  void useQicoreModelValidatorTestRatioHasMultipleIpsWithMultipleAssociations() {
    assertNotNull(modelValidatorFactory);
    List<Stratification> strats =
        List.of(
            Stratification.builder()
                .cqlDefinition("test")
                .associations(
                    List.of(PopulationType.INITIAL_POPULATION, PopulationType.DENOMINATOR))
                .build());

    List<Population> populations =
        List.of(
            Population.builder().name(PopulationType.INITIAL_POPULATION).build(),
            Population.builder().name(PopulationType.INITIAL_POPULATION).build());

    Group group =
        Group.builder()
            .populations(populations)
            .scoring(MeasureScoring.RATIO.toString())
            .stratifications(strats)
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);

    Exception ex =
        assertThrows(InvalidGroupException.class, () -> validator.validateGroupAssociations(group));
    assertThat(ex.getMessage(), is(equalTo("QiCore group stratifications cannot be associated.")));
  }

  @Test
  void useQicoreModelValidatorTestRatioHasMultipleIpsWithSingleAssociation() {
    assertNotNull(modelValidatorFactory);
    List<Stratification> strats =
        List.of(
            Stratification.builder()
                .cqlDefinition("test")
                .associations(List.of(PopulationType.INITIAL_POPULATION))
                .build());

    List<Population> populations =
        List.of(
            Population.builder().name(PopulationType.INITIAL_POPULATION).build(),
            Population.builder().name(PopulationType.INITIAL_POPULATION).build());

    Group group =
        Group.builder()
            .populations(populations)
            .scoring(MeasureScoring.RATIO.toString())
            .stratifications(strats)
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);

    try {
      validator.validateGroupAssociations(group);

    } catch (Exception e) {
      fail(e);
    }
  }

  @Test
  void useQicoreModelValidatorTestRatioHasSingleIpWithMultipleAssociations() {
    assertNotNull(modelValidatorFactory);
    List<Stratification> strats =
        List.of(
            Stratification.builder()
                .cqlDefinition("test")
                .associations(
                    List.of(PopulationType.INITIAL_POPULATION, PopulationType.DENOMINATOR))
                .build());

    List<Population> populations =
        List.of(Population.builder().name(PopulationType.INITIAL_POPULATION).build());

    Group group =
        Group.builder()
            .populations(populations)
            .scoring(MeasureScoring.RATIO.toString())
            .stratifications(strats)
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);

    try {
      validator.validateGroupAssociations(group);

    } catch (Exception e) {
      fail(e);
    }
  }

  @Test
  void useQicoreModelValidatorTestMeasureHasNoGroup() {
    assertNotNull(modelValidatorFactory);
    Measure measure = Measure.builder().id("1").groups(List.of()).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    try {
      validator.validateGroups(measure);
      fail("Should fail because, there should be at least one population criteria");
    } catch (InvalidResourceStateException e) {
      assertEquals(
          "Response could not be completed for Measure with ID 1, since there is no population criteria on the measure.",
          e.getMessage());
    }
  }

  @Test
  void useQicoreModelValidatorTestMeasureHasGroupWithNoTypes() {
    assertNotNull(modelValidatorFactory);
    Group group = Group.builder().build();
    Measure measure = Measure.builder().id("1").groups(List.of(group)).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    try {
      validator.validateGroups(measure);
      fail("Should fail because QICore group should have types");
    } catch (InvalidResourceStateException e) {
      assertEquals(
          "Response could not be completed for Measure with ID 1, since there is at least one Population Criteria with no type.",
          e.getMessage());
    }
  }

  @Test
  void useQicoreModelValidatorTestCompositeMeasureWithPopulationsThrowsException() {
    assertNotNull(modelValidatorFactory);
    Population population = Population.builder().name(PopulationType.INITIAL_POPULATION).build();
    Group group =
        Group.builder()
            .populations(List.of(population))
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setComposite(true);
    Measure measure =
        Measure.builder().id("1").groups(List.of(group)).measureMetaData(metaData).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    try {
      validator.validateGroups(measure);
      fail("Should fail because composite measures cannot have populations or stratifications");
    } catch (InvalidResourceStateException e) {
      assertEquals(
          "Response could not be completed for Measure with ID 1, since composite measures cannot have populations or stratifications in groups at this time.",
          e.getMessage());
    }
  }

  @Test
  void useQicoreModelValidatorTestCompositeMeasureWithStratificationsThrowsException() {
    assertNotNull(modelValidatorFactory);
    Stratification strat = new Stratification();
    strat.setCqlDefinition("test");
    Group group =
        Group.builder()
            .stratifications(List.of(strat))
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .build();
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setComposite(true);
    Measure measure =
        Measure.builder().id("1").groups(List.of(group)).measureMetaData(metaData).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    try {
      validator.validateGroups(measure);
      fail("Should fail because composite measures cannot have populations or stratifications");
    } catch (InvalidResourceStateException e) {
      assertEquals(
          "Response could not be completed for Measure with ID 1, since composite measures cannot have populations or stratifications in groups at this time.",
          e.getMessage());
    }
  }

  @Test
  void testQiCoreModelValidatorTestMeasureDoesHaveMeasuretypes() {
    assertNotNull(modelValidatorFactory);
    Measure measure =
        Measure.builder()
            .id("1")
            .groups(
                List.of(
                    Group.builder().measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME)).build()))
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    assertDoesNotThrow(() -> validator.validateGroups(measure));
  }

  @Test
  void testValidateCqlErrorsWhenMeasureHasCQLErrors() {
    assertNotNull(modelValidatorFactory);
    Measure measure =
        Measure.builder()
            .id("1")
            .errors(List.of(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES))
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    assertThrows(InvalidResourceStateException.class, () -> validator.validateCqlErrors(measure));
  }

  @Test
  void testValidateCqlErrorsWhenIsCQLErrorIsTrue() {
    assertNotNull(modelValidatorFactory);
    Measure measure = Measure.builder().id("1").cqlErrors(true).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QDM_5_6);
    assertTrue(validator instanceof QdmModelValidator);
    assertThrows(InvalidResourceStateException.class, () -> validator.validateCqlErrors(measure));
  }

  @Test
  void testValidateCqlErrorsWhenNoErrors() {
    assertNotNull(modelValidatorFactory);
    Measure measure =
        Measure.builder().id("1").errors(Collections.emptyList()).cqlErrors(false).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    assertDoesNotThrow(() -> validator.validateCqlErrors(measure));
  }

  @Test
  void testQdmModelValidatorTestMeasureHasNoMeasuretypes() {
    assertNotNull(modelValidatorFactory);
    QdmMeasure measure =
        QdmMeasure.builder().id("1").groups(List.of(Group.builder().build())).build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QDM_5_6);
    assertTrue(validator instanceof QdmModelValidator);
    try {
      validator.validateGroups(measure);
      fail("Should fail because, there should be at least one population criteria");
    } catch (InvalidResourceStateException e) {
      assertEquals(
          "Response could not be completed for Measure with ID 1, since there are no measure types for the measure.",
          e.getMessage());
    }
  }

  @Test
  void testQdmModelValidatorTestMeasureDoesHaveMeasuretypes() {
    assertNotNull(modelValidatorFactory);
    QdmMeasure measure =
        QdmMeasure.builder()
            .id("1")
            .groups(List.of(Group.builder().build()))
            .baseConfigurationTypes(List.of(BaseConfigurationTypes.OUTCOME))
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QDM_5_6);
    assertTrue(validator instanceof QdmModelValidator);
    assertDoesNotThrow(() -> validator.validateGroups(measure));
  }

  @Test
  void
      useQicoreModelValidatorTestDraftMeasureHasNoImprovementNotationInAtLeastOnePopulationCriteria() {
    assertNotNull(modelValidatorFactory);
    Group group1 =
        Group.builder()
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .improvementNotation("Decreased score indicates improvement")
            .build();
    Group group2 =
        Group.builder()
            .scoring(MeasureScoring.RATIO.toString())
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .build();

    Measure measure =
        Measure.builder()
            .id("1")
            .groups(List.of(group1, group2))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();

    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    try {
      validator.validateGroups(measure);
      fail(
          "Should fail because, since there is at least one Population Criteria with no improvement notation");
    } catch (InvalidResourceStateException e) {
      assertEquals(
          "Response could not be completed for Measure with ID 1, since there is at least one Population Criteria with no improvement notation.",
          e.getMessage());
    }
  }

  @Test
  void
      useQicoreModelValidatorTestDraftMeasureWithCompositeScoringDoesNotRequireImprovementNotation() {
    assertNotNull(modelValidatorFactory);
    Group group =
        Group.builder()
            .scoring(MeasureScoring.COMPOSITE.toString())
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .build();

    Measure measure =
        Measure.builder()
            .id("1")
            .groups(List.of(group))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();

    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    assertDoesNotThrow(() -> validator.validateGroups(measure));
  }

  @Test
  void useQicoreModelValidatorTestDraftMeasureWithCohortScoringDoesNotRequireImprovementNotation() {
    assertNotNull(modelValidatorFactory);
    Group group =
        Group.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .build();

    Measure measure =
        Measure.builder()
            .id("1")
            .groups(List.of(group))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();

    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    assertDoesNotThrow(() -> validator.validateGroups(measure));
  }

  @Test
  void
      useQicoreModelValidatorTestVersionedMeasureHasNoImprovementNotationInAtLeastOnePopulationCriteria() {
    assertNotNull(modelValidatorFactory);
    Group group1 =
        Group.builder()
            .scoring(MeasureScoring.PROPORTION.toString())
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .improvementNotation("Decreased score indicates improvement")
            .build();
    Group group2 =
        Group.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .build();

    Measure measure =
        Measure.builder()
            .id("1")
            .groups(List.of(group1, group2))
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .build();

    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertTrue(validator instanceof QiCoreModelValidator);
    assertDoesNotThrow(() -> validator.validateGroups(measure));
  }

  @Test
  public void testValidateMetadataNoMetadata() {
    Measure measure = Measure.builder().build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    //    measureUtil.validateMetadata(measure);
    assertDoesNotThrow(() -> validator.validateMetadata(measure));
  }

  @Test
  public void testValidateMetadataNoDevelopers() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureMetaData(MeasureMetaData.builder().build())
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);

    Exception ex =
        assertThrows(
            InvalidResourceStateException.class, () -> validator.validateMetadata(measure));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "Response could not be completed for Measure with ID measureId, since there are no associated developers in metadata.")));
  }

  @Test
  public void testValidateMetadataNoSteward() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureMetaData(
                MeasureMetaData.builder()
                    .developers(List.of(Organization.builder().id("OrgId").build()))
                    .build())
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    Exception ex =
        assertThrows(
            InvalidResourceStateException.class, () -> validator.validateMetadata(measure));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "Response could not be completed for Measure with ID measureId, since there is no associated steward in metadata.")));
  }

  @Test
  public void testValidateMetadataNoDescription() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureMetaData(
                MeasureMetaData.builder()
                    .developers(List.of(Organization.builder().id("OrgId").build()))
                    .steward(Organization.builder().id("OrgId").build())
                    .build())
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    Exception ex =
        assertThrows(
            InvalidResourceStateException.class, () -> validator.validateMetadata(measure));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "Response could not be completed for Measure with ID measureId, since there is no description in metadata.")));
  }

  @Test
  public void testValidateMetadataValid() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureMetaData(
                MeasureMetaData.builder()
                    .developers(List.of(Organization.builder().id("OrgId").build()))
                    .steward(Organization.builder().id("OrgId").build())
                    .description("test description")
                    .build())
            .build();
    ModelValidator validator = modelValidatorFactory.getModelValidator(ModelType.QI_CORE);
    assertDoesNotThrow(() -> validator.validateMetadata(measure));
  }
}
