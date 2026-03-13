package cms.gov.madie.measure.config.mongock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import gov.cms.madie.models.common.MeasureSetActionLog;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "merge_duplicate_measure_set_action_logs", order = "1", author = "madie_dev")
public class MergeDuplicateMeasureSetActionLogsChangeUnit {

  private List<MeasureSetActionLog> deletedDuplicates = new ArrayList<>();
  private List<MeasureSetActionLog> originalOldest = new ArrayList<>();

  @Execution
  public void mergeDuplicateMeasureSetActionLogs(
      MeasureSetActionLogRepository measureSetActionLogRepository) {
    List<MeasureSetActionLog> allLogs = measureSetActionLogRepository.findAll();

    Map<String, List<MeasureSetActionLog>> logsByTargetId =
        allLogs.stream().collect(Collectors.groupingBy(MeasureSetActionLog::getTargetId));

    logsByTargetId.forEach(
        (targetId, logs) -> {
          if (logs.size() <= 1) {
            return;
          }

          log.info(
              "Found [{}] MeasureSetActionLog documents for targetId: [{}]", logs.size(), targetId);

          mergeAndRemoveDuplicates(targetId, logs, measureSetActionLogRepository);
        });

    log.info(
        "Migration complete: merged [{}] targetIds, removed [{}] duplicate documents",
        originalOldest.size(),
        deletedDuplicates.size());
  }

  private void mergeAndRemoveDuplicates(
      String targetId,
      List<MeasureSetActionLog> logs,
      MeasureSetActionLogRepository measureSetActionLogRepository) {

    // Sort by id ascending (MongoDB ObjectIds are chronologically ordered), so oldest comes first
    logs.sort((a, b) -> a.getId().compareTo(b.getId()));

    MeasureSetActionLog oldest = logs.get(0);
    List<MeasureSetActionLog> duplicates = logs.subList(1, logs.size());

    // Snapshot original state for rollback
    originalOldest.add(
        MeasureSetActionLog.builder()
            .id(oldest.getId())
            .targetId(oldest.getTargetId())
            .actions(new ArrayList<>(oldest.getActions()))
            .build());

    // Merge all actions from duplicates into the oldest document
    for (MeasureSetActionLog duplicate : duplicates) {
      if (duplicate.getActions() != null) {
        oldest.getActions().addAll(duplicate.getActions());
      }
    }

    measureSetActionLogRepository.save(oldest);
    measureSetActionLogRepository.deleteAll(duplicates);
    deletedDuplicates.addAll(duplicates);

    log.info(
        "Merged targetId: [{}] — kept document [{}], removed [{}] duplicates with ids: [{}]",
        targetId,
        oldest.getId(),
        duplicates.size(),
        duplicates.stream().map(MeasureSetActionLog::getId).toList());
  }

  @RollbackExecution
  public void rollbackExecution(MeasureSetActionLogRepository measureSetActionLogRepository) {
    log.debug("Entering rollbackExecution() for merge_duplicate_measure_set_action_logs");

    // Restore documents to their original state
    for (MeasureSetActionLog original : originalOldest) {
      measureSetActionLogRepository.save(original);
    }

    // Re-insert deleted duplicates
    if (!deletedDuplicates.isEmpty()) {
      measureSetActionLogRepository.saveAll(deletedDuplicates);
    }
  }
}
