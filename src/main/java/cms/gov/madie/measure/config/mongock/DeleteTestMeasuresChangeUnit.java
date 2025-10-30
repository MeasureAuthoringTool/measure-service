package cms.gov.madie.measure.config.mongock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import gov.cms.madie.models.common.ActionLog;

import org.apache.commons.collections4.CollectionUtils;

import cms.gov.madie.measure.repositories.ActionLogRepositoryImpl;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import gov.cms.madie.models.measure.TestCase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "delete_test_measures", order = "1", author = "madie_dev")
public class DeleteTestMeasuresChangeUnit {
  private final List<String> users =
      Arrays.asList("cvasile", "bwelch", "pvasireddy", "colin.sullivan", "adongare");
  private final Set<String> userSet = new HashSet<>(users);

  private List<ActionLog> filteredTestCaseActionLogs = new ArrayList<>();
  private List<ActionLog> filteredMeasureActionLogs = new ArrayList<>();
  private List<ActionLog> filteredMeasureSetActionLogs = new ArrayList<>();
  private List<Export> filteredExports = new ArrayList<>();
  private List<Measure> filteredMeasures = new ArrayList<>();
  private List<MeasureSet> filteredMeasureSets = new ArrayList<>();

  @Execution
  public void deleteTestMeasures(
      MeasureRepository measureRepository,
      MeasureSetRepository measureSetRepository,
      ActionLogRepositoryImpl actionLogRepository,
      ExportRepository exportRepository) {

    log.info("\nDelete ActionLogs");
    // 1. delete all TestCaseActionLogs
    deleteTestCaseActionLogs(actionLogRepository);

    // 2: delete all MeasureActionLogs
    deleteMeasureActionLogs(actionLogRepository);

    // 3: delete all MeasureSetActionLogs
    deleteMeasureSetActionLogs(actionLogRepository);
    log.info("end of Delete ActionLogs\n");

    // get all users' measure and measure set ids
    List<MeasureSet> measureSets = measureSetRepository.findAll();
    filteredMeasureSets =
        measureSets.stream().filter(ms -> userSet.contains(ms.getOwner())).toList();
    if (CollectionUtils.isNotEmpty(filteredMeasureSets)) {
      List<String> filteredMeasureSetIds =
          filteredMeasureSets.stream().map(MeasureSet::getMeasureSetId).toList();

      filteredMeasures = measureRepository.findByMeasureSetIdIn(filteredMeasureSetIds);

      if (CollectionUtils.isNotEmpty(filteredMeasures)) {
        List<String> filteredMeasureIds = filteredMeasures.stream().map(Measure::getId).toList();
        // 4: delete all Exports
        deleteExports(exportRepository, filteredMeasureIds);

        // 5. delete all Measures
        deleteMeasures(measureRepository, filteredMeasures);
      }

      // 6. delete all MeasureSets
      deleteMeasureSets(measureSetRepository, filteredMeasureSets);
    }
  }

  int deleteTestCaseActionLogs(ActionLogRepositoryImpl actionLogRepository) {
    List<ActionLog> testCaseActionLogs = actionLogRepository.findAllActionLogs(TestCase.class);
    log.info("TestCaseAction total: {}", testCaseActionLogs.size());
    int toBeDeleted = 0;
    filteredTestCaseActionLogs =
        testCaseActionLogs.stream()
            .filter(
                log ->
                    log.getActions().stream()
                        .allMatch(action -> userSet.contains(action.getPerformedBy())))
            .toList();
    if (CollectionUtils.isNotEmpty(filteredTestCaseActionLogs)) {
      toBeDeleted = filteredTestCaseActionLogs.size();
      log.info("TestCaseActionLog to be deleted:  {}", toBeDeleted);
      actionLogRepository.removeActionsByUsers(users, TestCase.class);
    }
    return toBeDeleted;
  }

  int deleteMeasureActionLogs(ActionLogRepositoryImpl actionLogRepository) {
    List<ActionLog> measureActionLogs = actionLogRepository.findAllActionLogs(Measure.class);
    log.info("MeasureActionLog total: {}", measureActionLogs.size());
    int toBeDeleted = 0;
    filteredMeasureActionLogs =
        measureActionLogs.stream()
            .filter(
                log ->
                    log.getActions().stream()
                        .allMatch(action -> userSet.contains(action.getPerformedBy())))
            .toList();
    if (CollectionUtils.isNotEmpty(filteredMeasureActionLogs)) {
      toBeDeleted = filteredMeasureActionLogs.size();
      log.info("MeasureActionLog to be deleted:  {}", toBeDeleted);
      actionLogRepository.removeActionsByUsers(users, Measure.class);
    }
    return toBeDeleted;
  }

  int deleteMeasureSetActionLogs(ActionLogRepositoryImpl actionLogRepository) {
    List<ActionLog> measureSetActionLogs = actionLogRepository.findAllActionLogs(MeasureSet.class);
    log.info("MeasureSetActionLog total: {}", measureSetActionLogs.size());
    int toBeDeleted = 0;
    filteredMeasureSetActionLogs =
        measureSetActionLogs.stream()
            .filter(
                log ->
                    log.getActions().stream()
                        .allMatch(action -> userSet.contains(action.getPerformedBy())))
            .toList();

    if (CollectionUtils.isNotEmpty(filteredMeasureSetActionLogs)) {
      toBeDeleted = filteredMeasureSetActionLogs.size();
      log.info("MeasureSetActionLog to be deleted: {}", toBeDeleted);
      actionLogRepository.removeActionsByUsers(users, MeasureSet.class);
    }
    return toBeDeleted;
  }

  void deleteExports(ExportRepository exportRepository, List<String> filteredMeasureIds) {
    List<Export> export = exportRepository.findAll();
    log.info("Exports total = " + export.size());
    filteredExports =
        export.stream()
            .filter(ex -> filteredMeasureIds.contains(ex.getMeasureId()))
            .collect(Collectors.toList());
    if (CollectionUtils.isNotEmpty(filteredExports)) {
      exportRepository.deleteAll(filteredExports);
      log.info("Deleted Export size:  {}", filteredExports.size());
    }
  }

  void deleteMeasures(MeasureRepository measureRepository, List<Measure> filteredMeasures) {
    measureRepository.deleteAll(filteredMeasures);
    log.info("Deleted Measures: {}", filteredMeasures.size());
  }

  void deleteMeasureSets(
      MeasureSetRepository measureSetRepository, List<MeasureSet> filteredMeasureSets) {
    measureSetRepository.deleteAll(filteredMeasureSets);
    log.info("Deleted MeasureSets:  {}", filteredMeasureSets.size());
  }

  @RollbackExecution
  public void rollbackExecution(
      MeasureRepository measureRepository,
      MeasureSetRepository measureSetRepository,
      ActionLogRepositoryImpl actionLogRepository,
      ExportRepository exportRepository) {
    log.debug("Entering rollbackExecution()");
    rollBackTestCaseActionLogs(actionLogRepository);
    rollBackMeasureActionLogs(actionLogRepository);
    rollBackMeasureSetActionLogs(actionLogRepository);

    rollBackExports(exportRepository);
    rollBackMeasures(measureRepository);
    rollBackMeasureSets(measureSetRepository);
    log.debug("end of rollbackExecution()");
  }

  int rollBackTestCaseActionLogs(ActionLogRepositoryImpl actionLogRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredTestCaseActionLogs)) {
      List<ActionLog> savedTestCaseActionLogs =
          (List<ActionLog>)
              actionLogRepository.saveAllActionLogs(filteredTestCaseActionLogs, TestCase.class);
      size = savedTestCaseActionLogs != null ? savedTestCaseActionLogs.size() : 0;
      log.info("Roll back TestCaseActionLog: {}", size);
    }
    return size;
  }

  int rollBackMeasureActionLogs(ActionLogRepositoryImpl actionLogRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredMeasureActionLogs)) {
      List<ActionLog> savedMeasureActionLogs =
          (List<ActionLog>)
              actionLogRepository.saveAllActionLogs(filteredMeasureActionLogs, Measure.class);
      size = savedMeasureActionLogs != null ? savedMeasureActionLogs.size() : 0;
      log.info("Roll back MeasureActionLog: {}", size);
    }
    return size;
  }

  int rollBackMeasureSetActionLogs(ActionLogRepositoryImpl actionLogRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredMeasureSetActionLogs)) {
      List<ActionLog> savedMeasureSetActionLogs =
          (List<ActionLog>)
              actionLogRepository.saveAllActionLogs(filteredMeasureSetActionLogs, MeasureSet.class);
      size = savedMeasureSetActionLogs != null ? savedMeasureSetActionLogs.size() : 0;
      log.info("Roll back MeasureActionLog: {}", size);
    }
    return size;
  }

  int rollBackExports(ExportRepository exportRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredExports)) {
      List<Export> savedExports = exportRepository.saveAll(filteredExports);
      size = savedExports != null ? savedExports.size() : 0;
      log.info("Roll back Exports: {}", size);
    }
    return size;
  }

  int rollBackMeasures(MeasureRepository measureRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredMeasures)) {
      List<Measure> savedMeasures = measureRepository.saveAll(filteredMeasures);
      size = savedMeasures != null ? savedMeasures.size() : 0;
      log.info("Roll back Measure: {}", size);
    }
    return size;
  }

  int rollBackMeasureSets(MeasureSetRepository measureSetRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredMeasureSets)) {
      List<MeasureSet> savedMeasureSets = measureSetRepository.saveAll(filteredMeasureSets);
      size = savedMeasureSets != null ? savedMeasureSets.size() : 0;
      log.info("Roll back MeasureSets: {}", size);
    }
    return size;
  }
}
