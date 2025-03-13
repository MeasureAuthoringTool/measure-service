package cms.gov.madie.measure.config.mongock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.Stratification;

@ExtendWith(MockitoExtension.class)
public class UpdateStratificationAssociationTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private Measure measure;
  @InjectMocks private UpdateStratificationAssociation updateStratificationAssociation;

  private Measure testMeasure;

  private List<Group> testGroups;

  @BeforeEach
  public void setUp() {
    Stratification stratification = new Stratification();
    stratification.setAssociation(PopulationType.INITIAL_POPULATION);
    List<Group> groups = List.of(Group.builder().stratifications(List.of(stratification)).build());
    testMeasure =
        Measure.builder()
            .id("testMeasureId")
            .model(ModelType.QDM_5_6.getValue())
            .groups(groups)
            .build();
  }

  @Test
  public void updateStratificationAssociation() {
    doReturn(List.of(testMeasure))
        .when(measureRepository)
        .findAllByModel(ModelType.QDM_5_6.getValue());
    updateStratificationAssociation.removeAssociationFromStratification(measureRepository);

    Stratification strat = testMeasure.getGroups().get(0).getStratifications().get(0);
    assertNotNull(strat);

    assertNull(strat.getAssociation());
  }

  @Test
  public void updateStratificationAssociationWithoutGroups() {
    testMeasure.setGroups(new ArrayList<Group>());
    doReturn(List.of(testMeasure))
        .when(measureRepository)
        .findAllByModel(ModelType.QDM_5_6.getValue());
    updateStratificationAssociation.removeAssociationFromStratification(measureRepository);

    List<Group> groups = testMeasure.getGroups();
    assertTrue(CollectionUtils.isEmpty(groups));
  }

  @Test
  public void updateStratificationAssociationWithoutStratifications() {
    testMeasure.getGroups().get(0).setStratifications(new ArrayList<Stratification>());
    doReturn(List.of(testMeasure))
        .when(measureRepository)
        .findAllByModel(ModelType.QDM_5_6.getValue());
    updateStratificationAssociation.removeAssociationFromStratification(measureRepository);

    List<Stratification> strats = testMeasure.getGroups().get(0).getStratifications();
    assertTrue(CollectionUtils.isEmpty(strats));
  }

  @Test
  public void updateStratificationAssociation_ModelMismatch() {

    doReturn(new ArrayList<Measure>())
        .when(measureRepository)
        .findAllByModel(ModelType.QDM_5_6.getValue());
    updateStratificationAssociation.removeAssociationFromStratification(measureRepository);

    Stratification strat = testMeasure.getGroups().get(0).getStratifications().get(0);
    assertNotNull(strat);

    assertEquals(PopulationType.INITIAL_POPULATION, strat.getAssociation());
  }

  @Test
  public void testRollbackExecutionHasMeasures() {
    ReflectionTestUtils.setField(
        updateStratificationAssociation, "tempMeasures", List.of(testMeasure));

    updateStratificationAssociation.rollbackExecution(measureRepository);

    Stratification strat = testMeasure.getGroups().get(0).getStratifications().get(0);
    assertNotNull(strat);

    assertEquals(PopulationType.INITIAL_POPULATION, strat.getAssociation());
  }

  @Test
  public void testRollbackExecutionNoMeasures() {
    ReflectionTestUtils.setField(updateStratificationAssociation, "tempMeasures", new ArrayList());

    updateStratificationAssociation.rollbackExecution(measureRepository);

    Stratification strat = testMeasure.getGroups().get(0).getStratifications().get(0);
    assertNotNull(strat);

    assertEquals(PopulationType.INITIAL_POPULATION, strat.getAssociation());
  }
}
