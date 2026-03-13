package cms.gov.madie.measure.config.mongock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.MeasureSetActionLog;

@ExtendWith(MockitoExtension.class)
public class MergeDuplicateMeasureSetActionLogsChangeUnitTest {

  @Mock private MeasureSetActionLogRepository measureSetActionLogRepository;
  @InjectMocks private MergeDuplicateMeasureSetActionLogsChangeUnit changeUnit;

  private static final String MEASURE_SET_ID_1 = "measureSetId1";
  private static final String MEASURE_SET_ID_2 = "measureSetId2";

  private static final String USER_1 = "userId1";
  private static final String USER_2 = "userId2";
  private static final String USER_3 = "userId3";
  private static final String USER_4 = "userId4";

  private AccessControlAction shareAction;
  private AccessControlAction unshareAction;

  @BeforeEach
  public void setUp() {
    shareAction =
        AccessControlAction.builder().actionType(ActionType.SHARED).sharedWith(USER_1).build();
    unshareAction =
        AccessControlAction.builder().actionType(ActionType.UNSHARED).sharedWith(USER_2).build();
  }

  @Test
  public void testNoDuplicates() {
    MeasureSetActionLog log1 =
        MeasureSetActionLog.builder()
            .id("id1")
            .targetId(MEASURE_SET_ID_1)
            .actions(new ArrayList<>(List.of(shareAction)))
            .build();
    MeasureSetActionLog log2 =
        MeasureSetActionLog.builder()
            .id("id2")
            .targetId(MEASURE_SET_ID_2)
            .actions(new ArrayList<>(List.of(unshareAction)))
            .build();

    when(measureSetActionLogRepository.findAll()).thenReturn(List.of(log1, log2));

    changeUnit.mergeDuplicateMeasureSetActionLogs(measureSetActionLogRepository);

    verify(measureSetActionLogRepository, never()).save(any(MeasureSetActionLog.class));
    verify(measureSetActionLogRepository, never()).deleteAll(any(List.class));
  }

  @Test
  public void testSingleTargetIdWithDuplicates() {
    MeasureSetActionLog log1 =
        MeasureSetActionLog.builder()
            .id("id1")
            .targetId(MEASURE_SET_ID_1)
            .actions(new ArrayList<>(List.of(shareAction)))
            .build();
    MeasureSetActionLog log2 =
        MeasureSetActionLog.builder()
            .id("id2")
            .targetId(MEASURE_SET_ID_1)
            .actions(new ArrayList<>(List.of(unshareAction)))
            .build();

    when(measureSetActionLogRepository.findAll()).thenReturn(List.of(log2, log1));

    changeUnit.mergeDuplicateMeasureSetActionLogs(measureSetActionLogRepository);

    // Oldest document (id1) kept with merged actions, duplicate (id2) deleted
    verify(measureSetActionLogRepository, times(1)).save(log1);
    verify(measureSetActionLogRepository, times(1)).deleteAll(List.of(log2));
    assertEquals(2, log1.getActions().size());
  }

  @Test
  public void testDuplicateWithNullActions() {
    MeasureSetActionLog log1 =
        MeasureSetActionLog.builder()
            .id("id1")
            .targetId(MEASURE_SET_ID_1)
            .actions(new ArrayList<>(List.of(shareAction)))
            .build();
    MeasureSetActionLog log2 =
        MeasureSetActionLog.builder().id("id2").targetId(MEASURE_SET_ID_1).actions(null).build();

    when(measureSetActionLogRepository.findAll()).thenReturn(List.of(log1, log2));

    changeUnit.mergeDuplicateMeasureSetActionLogs(measureSetActionLogRepository);

    verify(measureSetActionLogRepository, times(1)).save(any(MeasureSetActionLog.class));
    verify(measureSetActionLogRepository, times(1)).deleteAll(any(List.class));
    assertEquals(1, log1.getActions().size());
  }

  @Test
  public void testMultipleTargetIdsWithDuplicates() {
    AccessControlAction shareAction2 =
        AccessControlAction.builder().actionType(ActionType.SHARED).sharedWith(USER_3).build();
    AccessControlAction shareAction3 =
        AccessControlAction.builder().actionType(ActionType.SHARED).sharedWith(USER_4).build();

    MeasureSetActionLog logA1 =
        MeasureSetActionLog.builder()
            .id("id1")
            .targetId(MEASURE_SET_ID_1)
            .actions(new ArrayList<>(List.of(shareAction)))
            .build();
    MeasureSetActionLog logA2 =
        MeasureSetActionLog.builder()
            .id("id2")
            .targetId(MEASURE_SET_ID_1)
            .actions(new ArrayList<>(List.of(unshareAction)))
            .build();
    MeasureSetActionLog logB1 =
        MeasureSetActionLog.builder()
            .id("id3")
            .targetId(MEASURE_SET_ID_2)
            .actions(new ArrayList<>(List.of(shareAction2)))
            .build();
    MeasureSetActionLog logB2 =
        MeasureSetActionLog.builder()
            .id("id4")
            .targetId(MEASURE_SET_ID_2)
            .actions(new ArrayList<>(List.of(shareAction3)))
            .build();

    when(measureSetActionLogRepository.findAll()).thenReturn(List.of(logA1, logA2, logB1, logB2));

    changeUnit.mergeDuplicateMeasureSetActionLogs(measureSetActionLogRepository);

    // Each targetId merged independently
    verify(measureSetActionLogRepository, times(2)).save(any(MeasureSetActionLog.class));
    verify(measureSetActionLogRepository, times(2)).deleteAll(any(List.class));
    assertEquals(2, logA1.getActions().size());
    assertEquals(2, logB1.getActions().size());
  }

  @Test
  public void testRollbackRestoresOriginalState() {
    MeasureSetActionLog log1 =
        MeasureSetActionLog.builder()
            .id("id1")
            .targetId(MEASURE_SET_ID_1)
            .actions(new ArrayList<>(List.of(shareAction)))
            .build();
    MeasureSetActionLog log2 =
        MeasureSetActionLog.builder()
            .id("id2")
            .targetId(MEASURE_SET_ID_1)
            .actions(new ArrayList<>(List.of(unshareAction)))
            .build();

    when(measureSetActionLogRepository.findAll()).thenReturn(List.of(log1, log2));

    // Execute to populate rollback data
    changeUnit.mergeDuplicateMeasureSetActionLogs(measureSetActionLogRepository);

    // Rollback
    changeUnit.rollbackExecution(measureSetActionLogRepository);

    // save called twice: once during execution, once during rollback
    verify(measureSetActionLogRepository, times(2)).save(any(MeasureSetActionLog.class));
    // Deleted duplicates re-inserted
    verify(measureSetActionLogRepository, times(1)).saveAll(any(List.class));
  }
}
