package cms.gov.madie.measure.config.mongock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;

@ExtendWith(MockitoExtension.class)
public class GroupAndPopulationDisplayIdChangeUnitTest {
  @Mock private MeasureRepository measureRepository;
  @InjectMocks private GroupAndPopulationDisplayIdChangeUnit changeUnit;

  private Measure measure;
  private Group group;

  @BeforeEach
  void setUp() {
    Population ip =
        Population.builder().id("testPopulationId").name(PopulationType.INITIAL_POPULATION).build();
    group = Group.builder().id("testGroupId").populations(List.of(ip)).build();
    measure = Measure.builder().id("testMeasureId").model(ModelType.QI_CORE.toString()).build();
  }

  @Test
  void testDoesNotSetDisplayIdsForEmptyMeasures() throws Exception {
    when(measureRepository.findAll()).thenReturn(List.of());

    changeUnit.setDisplayIds(measureRepository);

    verify(measureRepository, new Times(1)).findAll();
  }

  @Test
  void testDoesNotSetDisplayIdsForQdmMeasures() throws Exception {
    measure.setModel(ModelType.QDM_5_6.toString());

    when(measureRepository.findAll()).thenReturn(List.of(measure));

    changeUnit.setDisplayIds(measureRepository);

    verify(measureRepository, new Times(1)).findAll();
  }

  @Test
  void testDoesNotSetDisplayIdsForQiCoreMeasureWithNoGroups() throws Exception {

    when(measureRepository.findAll()).thenReturn(List.of(measure));

    changeUnit.setDisplayIds(measureRepository);

    verify(measureRepository, new Times(1)).findAll();
  }

  @Test
  void testSetDisplayIdsForGroupAndPopulations() throws Exception {
    measure.setGroups(List.of(group));

    when(measureRepository.findAll()).thenReturn(List.of(measure));

    changeUnit.setDisplayIds(measureRepository);

    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(measure);

    assertEquals("Group_1", measure.getGroups().get(0).getDisplayId());
    assertEquals(
        "InitialPopulation_1", measure.getGroups().get(0).getPopulations().get(0).getDisplayId());
  }

  @Test
  public void testRollbackExecutionHasMeasures() throws Exception {
    measure.setGroups(List.of(group));
    ReflectionTestUtils.setField(changeUnit, "tempMeasures", List.of(measure));

    changeUnit.rollbackExecution(measureRepository);

    verify(measureRepository, new Times(1)).save(measure);

    Group grup = measure.getGroups().get(0);
    assertNotNull(grup);

    assertNull(grup.getDisplayId());
  }

  @Test
  public void testRollbackExecutionNoMeasures() throws Exception {
    measure.setGroups(List.of(group));
    ReflectionTestUtils.setField(changeUnit, "tempMeasures", List.of());

    changeUnit.rollbackExecution(measureRepository);

    verifyNoInteractions(measureRepository);
  }
}
