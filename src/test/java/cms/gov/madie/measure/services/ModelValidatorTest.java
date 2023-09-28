package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import cms.gov.madie.measure.exceptions.InvalidGroupException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.Stratification;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class ModelValidatorTest {
  @Autowired private ModelValidatorLocator modelLocator;

  @Test
  void createModelValidatorTest() {
    assertNotNull(modelLocator);
  }

  @Test
  void createQdmModelValidatorTest() {
    assertNotNull(modelLocator);

    ModelValidator validator = modelLocator.get(ModelType.QDM_5_6.getShortValue());
    assertTrue(validator instanceof QdmModelValidator);
  }

  @Test
  void useQdmModelValidatorTest() {
    assertNotNull(modelLocator);
    Group group = Group.builder().stratifications(new ArrayList<Stratification>()).build();
    ModelValidator validator = modelLocator.get(ModelType.QDM_5_6.getShortValue());
    assertTrue(validator instanceof QdmModelValidator);
    try {
      validator.validateGroupAssociations(ModelType.QDM_5_6.getShortValue(), group);
    } catch (Exception e) {
      fail(e);
    }
    ;
  }

  @Test
  void useQdmModelValidatorTestHasInvalidAssociation() {
    assertNotNull(modelLocator);
    Stratification strat = new Stratification();
    List<Stratification> strats = new ArrayList<>();
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    ;
    strats.add(strat);

    Group group = Group.builder().stratifications(strats).build();
    ModelValidator validator = modelLocator.get(ModelType.QDM_5_6.getShortValue());
    assertTrue(validator instanceof QdmModelValidator);
    try {
      validator.validateGroupAssociations(ModelType.QDM_5_6.getShortValue(), group);
      fail("Should fail because association exists on the Stratification");
    } catch (Exception e) {
      assertTrue(e instanceof InvalidGroupException);
    }
  }

  @Test
  void useQdmModelValidatorTestHasValidAssociation() {
    assertNotNull(modelLocator);
    Stratification strat = new Stratification();
    List<Stratification> strats = new ArrayList<>();

    strats.add(strat);

    Group group = Group.builder().stratifications(strats).build();
    ModelValidator validator = modelLocator.get(ModelType.QDM_5_6.getShortValue());
    assertTrue(validator instanceof QdmModelValidator);
    try {
      validator.validateGroupAssociations(ModelType.QDM_5_6.getShortValue(), group);
    } catch (Exception e) {
      fail(e);
    }
  }

  @Test
  void createQicoreModelValidatorTest() {
    assertNotNull(modelLocator);

    ModelValidator validator = modelLocator.get(ModelType.QI_CORE.getShortValue());
    assertTrue(validator instanceof QicoreModelValidator);
  }

  @Test
  void useQicoreModelValidatorTestNoStratifications() {
    assertNotNull(modelLocator);
    Group group = Group.builder().stratifications(new ArrayList<Stratification>()).build();
    ModelValidator validator = modelLocator.get(ModelType.QI_CORE.getShortValue());
    assertTrue(validator instanceof QicoreModelValidator);
    try {
      validator.validateGroupAssociations(ModelType.QI_CORE.getShortValue(), group);

    } catch (Exception e) {
      fail(e);
    }
  }

  @Test
  void useQicoreModelValidatorTestHasInvalidAssociation() {
    assertNotNull(modelLocator);
    Stratification strat = new Stratification();
    List<Stratification> strats = new ArrayList<>();

    strats.add(strat);

    Group group = Group.builder().stratifications(strats).build();
    ModelValidator validator = modelLocator.get(ModelType.QI_CORE.getShortValue());
    assertTrue(validator instanceof QicoreModelValidator);
    try {
      validator.validateGroupAssociations(ModelType.QI_CORE.getShortValue(), group);
      fail("Should fail because QICore strat association can't be null");
    } catch (Exception e) {
      assertTrue(e instanceof InvalidGroupException);
    }
  }

  @Test
  void useQicoreModelValidatorTestHasAssociation() {
    assertNotNull(modelLocator);
    Stratification strat = new Stratification();
    List<Stratification> strats = new ArrayList<>();
    strat.setAssociation(PopulationType.INITIAL_POPULATION);
    ;
    strats.add(strat);

    Group group = Group.builder().stratifications(strats).build();
    ModelValidator validator = modelLocator.get(ModelType.QI_CORE.getShortValue());
    assertTrue(validator instanceof QicoreModelValidator);
    try {
      validator.validateGroupAssociations(ModelType.QI_CORE.getShortValue(), group);

    } catch (Exception e) {
      fail(e);
    }
  }
}