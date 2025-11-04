package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.ActionLogRepositoryImpl;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionLog;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class DeleteTestMeasuresChangeUnitTest {
  @Mock private MeasureRepository measureRepository;
  @Mock private MeasureSetRepository measureSetRepository;
  @Mock private ActionLogRepositoryImpl actionLogRepository;
  @Mock private ExportRepository exportRepository;
  @InjectMocks private DeleteTestMeasuresChangeUnit deleteTestMeasuresChangeUnit;

  private final List<String> users = Arrays.asList("testUser");
  private final Set<String> userSet = new HashSet<>(users);
  private Action action = null;
  private ActionLog actionLog = null;
  private Export export = null;
  private MeasureSet measureSet =
      MeasureSet.builder()
          .id("testMeasureSetId")
          .measureSetId("testMeasureSetId")
          .owner("testUser")
          .build();
  private Measure measure =
      Measure.builder().id("testMeasureId").measureSetId("testMeasureSetId").build();

  @BeforeEach
  void init() {
    ReflectionTestUtils.setField(deleteTestMeasuresChangeUnit, "userSet", userSet);
    action = Action.builder().actionType(ActionType.CREATED).performedBy("testUser").build();
    actionLog =
        ActionLog.builder()
            .id("testActionLogId")
            .targetId("testTargetId")
            .actions(List.of(action))
            .build();
    export = Export.builder().id("testExportId").measureId("testMeasureId").build();

    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredTestCaseActionLogs", List.of(actionLog));
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredMeasureActionLogs", List.of(actionLog));
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredMeasureSetActionLogs", List.of(actionLog));

    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredExports", List.of(actionLog));
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredMeasures", List.of(measure));
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredMeasureSets", List.of(measureSet));
  }

  @Test
  void testDeleteTestCaseActionLogs() {
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));
    doNothing().when(actionLogRepository).removeActionsByUsers(anyList(), any());
    deleteTestMeasuresChangeUnit.deleteTestCaseActionLogs(actionLogRepository);

    verify(actionLogRepository, times(1)).findAllActionLogs(any());
    verify(actionLogRepository, times(1)).removeActionsByUsers(anyList(), any());
  }

  @Test
  void testDeleteTestCaseActionLogsTestCaseLogNotDeleted() {
    action.setPerformedBy("anotherUser");
    actionLog.setActions(List.of(action));
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));

    deleteTestMeasuresChangeUnit.deleteTestCaseActionLogs(actionLogRepository);

    verify(actionLogRepository, times(1)).findAllActionLogs(any());
    verify(actionLogRepository, times(0)).removeActionsByUsers(anyList(), any());
  }

  @Test
  void testDeleteMeasurectionLogs() {
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));
    doNothing().when(actionLogRepository).removeActionsByUsers(anyList(), any());

    deleteTestMeasuresChangeUnit.deleteMeasureActionLogs(actionLogRepository);

    verify(actionLogRepository, times(1)).findAllActionLogs(any());
    verify(actionLogRepository, times(1)).removeActionsByUsers(anyList(), any());
  }

  @Test
  void testDeleteMeasureActionLogsMeasureLogNotDeleted() {
    action.setPerformedBy("anotherUser");
    actionLog.setActions(List.of(action));
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));

    deleteTestMeasuresChangeUnit.deleteMeasureActionLogs(actionLogRepository);

    verify(actionLogRepository, times(1)).findAllActionLogs(any());
    verify(actionLogRepository, times(0)).removeActionsByUsers(anyList(), any());
  }

  @Test
  void testDeleteMeasureSetctionLogs() {
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));
    doNothing().when(actionLogRepository).removeActionsByUsers(anyList(), any());

    deleteTestMeasuresChangeUnit.deleteMeasureSetActionLogs(actionLogRepository);

    verify(actionLogRepository, times(1)).findAllActionLogs(any());
    verify(actionLogRepository, times(1)).removeActionsByUsers(anyList(), any());
  }

  @Test
  void testDeleteMeasureSetActionLogsMeasureLogNotDeleted() {
    action.setPerformedBy("anotherUser");
    actionLog.setActions(List.of(action));
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));

    deleteTestMeasuresChangeUnit.deleteMeasureSetActionLogs(actionLogRepository);

    verify(actionLogRepository, times(1)).findAllActionLogs(any());
    verify(actionLogRepository, times(0)).removeActionsByUsers(anyList(), any());
  }

  @Test
  void testDeleteExports() {
    when(exportRepository.findAll())
        .thenReturn(
            List.of(Export.builder().id("testExportId").measureId("testMeasureId").build()));

    deleteTestMeasuresChangeUnit.deleteExports(exportRepository, List.of("testMeasureId"));

    verify(exportRepository, times(1)).findAll();
    verify(exportRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteExportsNoDelete() {
    export.setMeasureId("anotherMeasureId");
    when(exportRepository.findAll()).thenReturn(List.of(export));

    deleteTestMeasuresChangeUnit.deleteExports(exportRepository, List.of("testMeasureId"));

    verify(exportRepository, times(1)).findAll();
    verify(exportRepository, times(0)).deleteAll(anyList());
  }

  @Test
  void testDeleteMeasures() {
    doNothing().when(measureRepository).deleteAll(anyList());

    deleteTestMeasuresChangeUnit.deleteMeasures(
        measureRepository, List.of(Measure.builder().build()));

    verify(measureRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteMeasureSets() {
    doNothing().when(measureSetRepository).deleteAll(anyList());

    deleteTestMeasuresChangeUnit.deleteMeasureSets(
        measureSetRepository, List.of(MeasureSet.builder().build()));

    verify(measureSetRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteTestMeasures() {
    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet));
    when(measureRepository.findByMeasureSetIdIn(anyList())).thenReturn(List.of(measure));

    // 1. TestCaseActionLog
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));
    doNothing().when(actionLogRepository).removeActionsByUsers(anyList(), any());

    // 2. MeasureActionLogs
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));

    // 3. MeasureSetActionLogs
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));

    // 4. Export
    when(exportRepository.findAll()).thenReturn(List.of(export));

    // 5. Measure
    doNothing().when(measureRepository).deleteAll(anyList());

    // 6. MeasureSet
    doNothing().when(measureSetRepository).deleteAll(anyList());

    deleteTestMeasuresChangeUnit.deleteTestMeasures(
        measureRepository, measureSetRepository, actionLogRepository, exportRepository);

    verify(actionLogRepository, times(3)).findAllActionLogs(any());
    verify(actionLogRepository, times(3)).removeActionsByUsers(anyList(), any());

    verify(measureSetRepository, times(1)).findAll();
    verify(measureRepository, times(1)).findByMeasureSetIdIn(anyList());

    verify(exportRepository, times(1)).deleteAll(anyList());
    verify(measureRepository, times(1)).deleteAll(anyList());
    verify(measureSetRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteTestMeasuresNoMeasures() {
    when(measureSetRepository.findAll()).thenReturn(List.of(measureSet));
    when(measureRepository.findByMeasureSetIdIn(anyList())).thenReturn(Collections.emptyList());

    // 1. TestCaseActionLog
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));
    doNothing().when(actionLogRepository).removeActionsByUsers(anyList(), any());

    // 2. MeasureActionLogs
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));

    // 3. MeasureSetActionLogs
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));

    // 6. MeasureSet
    doNothing().when(measureSetRepository).deleteAll(anyList());

    deleteTestMeasuresChangeUnit.deleteTestMeasures(
        measureRepository, measureSetRepository, actionLogRepository, exportRepository);

    verify(actionLogRepository, times(3)).findAllActionLogs(any());
    verify(actionLogRepository, times(3)).removeActionsByUsers(anyList(), any());

    verify(measureSetRepository, times(1)).findAll();
    verify(measureRepository, times(1)).findByMeasureSetIdIn(anyList());

    verify(exportRepository, times(0)).deleteAll(anyList());
    verify(measureRepository, times(0)).deleteAll(anyList());
    verify(measureSetRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteTestMeasuresNoMeasureSet() {
    when(measureSetRepository.findAll()).thenReturn(Collections.emptyList());

    // 1. TestCaseActionLog
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));
    doNothing().when(actionLogRepository).removeActionsByUsers(anyList(), any());

    // 2. MeasureActionLogs
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));

    // 3. MeasureSetActionLogs
    when(actionLogRepository.findAllActionLogs(any())).thenReturn(List.of(actionLog));

    deleteTestMeasuresChangeUnit.deleteTestMeasures(
        measureRepository, measureSetRepository, actionLogRepository, exportRepository);

    verify(actionLogRepository, times(3)).findAllActionLogs(any());
    verify(actionLogRepository, times(3)).removeActionsByUsers(anyList(), any());

    verify(measureSetRepository, times(1)).findAll();
    verify(measureRepository, times(0)).findByMeasureSetIdIn(anyList());

    verify(exportRepository, times(0)).deleteAll(anyList());
    verify(measureRepository, times(0)).deleteAll(anyList());
    verify(measureSetRepository, times(0)).deleteAll(anyList());
  }

  @Test
  void testRollBackTestCaseActionLogs() {
    when(actionLogRepository.saveAllActionLogs(anyList(), any())).thenReturn(List.of(actionLog));
    int num = deleteTestMeasuresChangeUnit.rollBackTestCaseActionLogs(actionLogRepository);
    verify(actionLogRepository, times(1)).saveAllActionLogs(anyList(), any());
    assertTrue(num == 1);
  }

  @Test
  void testRollBackTestCaseActionLogsNotSaved() {
    when(actionLogRepository.saveAllActionLogs(anyList(), any())).thenReturn(null);
    int num = deleteTestMeasuresChangeUnit.rollBackTestCaseActionLogs(actionLogRepository);
    verify(actionLogRepository, times(1)).saveAllActionLogs(anyList(), any());
    assertTrue(num == 0);
  }

  @Test
  void testRollBackMeasureActionLogs() {
    when(actionLogRepository.saveAllActionLogs(anyList(), any())).thenReturn(List.of(actionLog));
    int num = deleteTestMeasuresChangeUnit.rollBackMeasureActionLogs(actionLogRepository);
    verify(actionLogRepository, times(1)).saveAllActionLogs(anyList(), any());
    assertTrue(num == 1);
  }

  @Test
  void testRollBackMeasureActionLogsNotSaved() {
    when(actionLogRepository.saveAllActionLogs(anyList(), any())).thenReturn(null);
    int num = deleteTestMeasuresChangeUnit.rollBackMeasureActionLogs(actionLogRepository);
    verify(actionLogRepository, times(1)).saveAllActionLogs(anyList(), any());
    assertTrue(num == 0);
  }

  @Test
  void testRollBackMeasureSetActionLogs() {
    when(actionLogRepository.saveAllActionLogs(anyList(), any())).thenReturn(List.of(actionLog));
    int num = deleteTestMeasuresChangeUnit.rollBackMeasureSetActionLogs(actionLogRepository);
    verify(actionLogRepository, times(1)).saveAllActionLogs(anyList(), any());
    assertTrue(num == 1);
  }

  @Test
  void testRollBackMeasureSetActionLogsNotSaved() {
    when(actionLogRepository.saveAllActionLogs(anyList(), any())).thenReturn(null);
    int num = deleteTestMeasuresChangeUnit.rollBackMeasureSetActionLogs(actionLogRepository);
    verify(actionLogRepository, times(1)).saveAllActionLogs(anyList(), any());
    assertTrue(num == 0);
  }

  @Test
  void testRollBackExports() {
    when(exportRepository.saveAll(anyList())).thenReturn(List.of(export));
    int num = deleteTestMeasuresChangeUnit.rollBackExports(exportRepository);
    verify(exportRepository, times(1)).saveAll(anyList());
    assertTrue(num == 1);
  }

  @Test
  void testRollBackExportsNotSaved() {
    when(exportRepository.saveAll(anyList())).thenReturn(null);
    int num = deleteTestMeasuresChangeUnit.rollBackExports(exportRepository);
    verify(exportRepository, times(1)).saveAll(anyList());
    assertTrue(num == 0);
  }

  @Test
  void testRollBackMeasures() {
    when(measureRepository.saveAll(anyList())).thenReturn(List.of(measure));
    int num = deleteTestMeasuresChangeUnit.rollBackMeasures(measureRepository);
    verify(measureRepository, times(1)).saveAll(anyList());
    assertTrue(num == 1);
  }

  @Test
  void testRollBackMeasuresNotSaved() {
    when(measureRepository.saveAll(anyList())).thenReturn(null);
    int num = deleteTestMeasuresChangeUnit.rollBackMeasures(measureRepository);
    verify(measureRepository, times(1)).saveAll(anyList());
    assertTrue(num == 0);
  }

  @Test
  void testRollBackMeasureSets() {
    when(measureSetRepository.saveAll(anyList())).thenReturn(List.of(measureSet));
    int num = deleteTestMeasuresChangeUnit.rollBackMeasureSets(measureSetRepository);
    verify(measureSetRepository, times(1)).saveAll(anyList());
    assertTrue(num == 1);
  }

  @Test
  void testRollBackMeasureSetsNotSaved() {
    when(measureSetRepository.saveAll(anyList())).thenReturn(null);
    int num = deleteTestMeasuresChangeUnit.rollBackMeasureSets(measureSetRepository);
    verify(measureSetRepository, times(1)).saveAll(anyList());
    assertTrue(num == 0);
  }

  @Test
  void testRollbackExecution() {
    when(actionLogRepository.saveAllActionLogs(anyList(), any())).thenReturn(List.of(actionLog));
    when(actionLogRepository.saveAllActionLogs(anyList(), any())).thenReturn(List.of(actionLog));
    when(actionLogRepository.saveAllActionLogs(anyList(), any())).thenReturn(List.of(actionLog));

    when(exportRepository.saveAll(anyList())).thenReturn(List.of(export));
    when(measureRepository.saveAll(anyList())).thenReturn(List.of(measure));
    when(measureSetRepository.saveAll(anyList())).thenReturn(List.of(measureSet));

    deleteTestMeasuresChangeUnit.rollbackExecution(
        measureRepository, measureSetRepository, actionLogRepository, exportRepository);

    verify(actionLogRepository, times(3)).saveAllActionLogs(anyList(), any());
    verify(exportRepository, times(1)).saveAll(anyList());
    verify(measureRepository, times(1)).saveAll(anyList());
    verify(measureSetRepository, times(1)).saveAll(anyList());
  }

  @Test
  void testRollbackExecutionNoRollBack() {
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredTestCaseActionLogs", Collections.emptyList());
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredMeasureActionLogs", Collections.emptyList());
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredMeasureSetActionLogs", Collections.emptyList());
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredExports", Collections.emptyList());
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredMeasures", Collections.emptyList());
    ReflectionTestUtils.setField(
        deleteTestMeasuresChangeUnit, "filteredMeasureSets", Collections.emptyList());

    deleteTestMeasuresChangeUnit.rollbackExecution(
        measureRepository, measureSetRepository, actionLogRepository, exportRepository);

    verify(actionLogRepository, times(0)).saveAllActionLogs(anyList(), any());
    verify(exportRepository, times(0)).saveAll(anyList());
    verify(measureRepository, times(0)).saveAll(anyList());
    verify(measureSetRepository, times(0)).saveAll(anyList());
  }
}
