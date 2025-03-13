package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateImprovementNotationQiCoreChangeUnitTest {
  @Mock private MeasureRepository measureRepository;

  @InjectMocks
  private UpdateImprovementNotationQiCoreChangeUnit updateImprovementNotationQiCoreChangeUnit;

  @Test
  void testUpdateImprovementNotation_withMixedModelTypes() {
    Measure measureQiCore =
        createTestMeasure(
            true,
            Arrays.asList(createTestGroup("decrease"), createTestGroup("increase")),
            ModelType.QI_CORE);

    Measure measureQdm =
        createTestMeasure(
            true, Collections.singletonList(createTestGroup("decrease")), ModelType.QDM_5_6);

    Measure measureQiCoreVersioned =
        createTestMeasure(
            false,
            Arrays.asList(createTestGroup("increase"), createTestGroup("decrease")),
            ModelType.QI_CORE);

    List<Measure> qiCoreMeasures = Arrays.asList(measureQiCore, measureQiCoreVersioned);
    when(measureRepository.findAllByModel(ModelType.QI_CORE.getValue())).thenReturn(qiCoreMeasures);
    updateImprovementNotationQiCoreChangeUnit.updateImprovementNotationQiCore(measureRepository);

    // Verify the QI_CORE measure is updated
    assertEquals(
        "Decreased score indicates improvement",
        measureQiCore.getGroups().get(0).getImprovementNotation());
    assertEquals(
        "Increased score indicates improvement",
        measureQiCore.getGroups().get(1).getImprovementNotation());

    // Verify the first QDM measure is not updated
    assertEquals("decrease", measureQdm.getGroups().get(0).getImprovementNotation());

    // Verify the non-draft measure is not updated
    assertEquals("increase", measureQiCoreVersioned.getGroups().get(0).getImprovementNotation());
    assertEquals("decrease", measureQiCoreVersioned.getGroups().get(1).getImprovementNotation());

    verify(measureRepository, times(1)).save(measureQiCore);
    verify(measureRepository, never()).save(measureQdm);
    verify(measureRepository, never()).save(measureQiCoreVersioned);
  }

  @Test
  void testUpdateImprovementNotation_noMeasuresToUpdate() {
    when(measureRepository.findAllByModel(ModelType.QI_CORE.getValue()))
        .thenReturn(Collections.emptyList());
    updateImprovementNotationQiCoreChangeUnit.updateImprovementNotationQiCore(measureRepository);
    verify(measureRepository, never()).save(any());
  }

  @Test
  void testUpdateImprovementNotation_withCorrectValues() {
    Measure measure =
        createTestMeasure(
            true,
            Arrays.asList(
                createTestGroup("Decreased score indicates improvement"),
                createTestGroup("Increased score indicates improvement")),
            ModelType.QI_CORE);

    when(measureRepository.findAllByModel(ModelType.QI_CORE.getValue()))
        .thenReturn(Collections.singletonList(measure));
    updateImprovementNotationQiCoreChangeUnit.updateImprovementNotationQiCore(measureRepository);

    assertEquals(
        "Decreased score indicates improvement",
        measure.getGroups().get(0).getImprovementNotation());
    assertEquals(
        "Increased score indicates improvement",
        measure.getGroups().get(1).getImprovementNotation());
    verify(measureRepository, never()).save(measure);
  }

  @Test
  void testRollback() {
    Measure measure1 =
        createTestMeasure(
            true, Collections.singletonList(createTestGroup("decrease")), ModelType.QI_CORE);
    Measure measure2 =
        createTestMeasure(
            true, Collections.singletonList(createTestGroup("increase")), ModelType.QI_CORE);

    when(measureRepository.findAllByModel(ModelType.QI_CORE.getValue()))
        .thenReturn(Arrays.asList(measure1, measure2));

    updateImprovementNotationQiCoreChangeUnit.updateImprovementNotationQiCore(measureRepository);
    updateImprovementNotationQiCoreChangeUnit.rollbackExecution(measureRepository);

    // rollback saves original measures
    verify(measureRepository, times(1)).save(measure1);
    verify(measureRepository, times(1)).save(measure2);
  }

  private Measure createTestMeasure(boolean isDraft, List<Group> groups, ModelType modelType) {
    return new Measure()
        .toBuilder()
            .model(modelType.getValue())
            .measureMetaData(new MeasureMetaData().toBuilder().draft(isDraft).build())
            .groups(groups)
            .build();
  }

  private Group createTestGroup(String improvementNotation) {
    return new Group().toBuilder().improvementNotation(improvementNotation).build();
  }
}
