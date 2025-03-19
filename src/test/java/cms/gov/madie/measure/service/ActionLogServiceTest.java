package cms.gov.madie.measure.service;

import cms.gov.madie.measure.repositories.MeasureActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.MeasureSetActionLog;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.services.ActionLogService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ActionLogServiceTest {
  @Mock MeasureActionLogRepository measureActionLogRepository;
  @Mock MeasureSetActionLogRepository measureSetActionLogRepository;

  @InjectMocks ActionLogService actionLogService;

  @Captor private ArgumentCaptor<Action> actionArgumentCaptor;

  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;
  @Captor private ArgumentCaptor<String> collectionArgumentCaptor;

  @Test
  void testLogActionReturnsTrue() {
    when(measureActionLogRepository.pushEvent(anyString(), any(Action.class), anyString()))
        .thenReturn(true);
    boolean output =
        actionLogService.logAction("TARGET_ID", Measure.class, ActionType.CREATED, "testUser");
    assertThat(output, is(true));
    verify(measureActionLogRepository, times(1))
        .pushEvent(
            targetIdArgumentCaptor.capture(),
            actionArgumentCaptor.capture(),
            collectionArgumentCaptor.capture());
    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    Action value = actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.CREATED)));
    assertThat(value.getPerformedBy(), is(equalTo("testUser")));
  }

  @Test
  void testLogActionReturnsFalse() {
    when(measureActionLogRepository.pushEvent(anyString(), any(Action.class), anyString()))
        .thenReturn(false);
    boolean output =
        actionLogService.logAction("TARGET_ID", Measure.class, ActionType.DELETED, "testUser");
    assertThat(output, is(false));
    verify(measureActionLogRepository, times(1))
        .pushEvent(
            targetIdArgumentCaptor.capture(),
            actionArgumentCaptor.capture(),
            collectionArgumentCaptor.capture());
    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    Action value = actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.DELETED)));
    assertThat(value.getPerformedBy(), is(equalTo("testUser")));
  }

  @Test
  void testLogShareAccessControlActionReturnsTrue() {
    when(measureSetActionLogRepository.pushEvent(
            anyString(), any(AccessControlAction.class), anyString()))
        .thenReturn(true);
    boolean output =
        actionLogService.logShareAccessControlAction(
            "TARGET_ID", MeasureSet.class, ActionType.SHARED, "testUser", "sharedWith");
    assertThat(output, is(true));
    verify(measureSetActionLogRepository, times(1))
        .pushEvent(
            targetIdArgumentCaptor.capture(),
            actionArgumentCaptor.capture(),
            collectionArgumentCaptor.capture());
    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    assertThat(actionArgumentCaptor.getValue(), instanceOf(AccessControlAction.class));
    AccessControlAction value = (AccessControlAction) actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.SHARED)));
    assertThat(value.getPerformedBy(), is(equalTo("testUser")));
    assertThat(value.getSharedWith(), is(equalTo("sharedWith")));
  }

  @Test
  void testShareLogAccessControlActionReturnsFalse() {
    when(measureSetActionLogRepository.pushEvent(
            anyString(), any(AccessControlAction.class), anyString()))
        .thenReturn(false);
    boolean output =
        actionLogService.logShareAccessControlAction(
            "TARGET_ID", MeasureSet.class, ActionType.SHARED, "testUser", "sharedWith");
    assertThat(output, is(false));
    verify(measureSetActionLogRepository, times(1))
        .pushEvent(
            targetIdArgumentCaptor.capture(),
            actionArgumentCaptor.capture(),
            collectionArgumentCaptor.capture());
    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    assertThat(actionArgumentCaptor.getValue(), instanceOf(AccessControlAction.class));
    AccessControlAction value = (AccessControlAction) actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.SHARED)));
    assertThat(value.getPerformedBy(), is(equalTo("testUser")));
    assertThat(value.getSharedWith(), is(equalTo("sharedWith")));
  }

  @Test
  void testFindMeasureSetActionLogByTargetId() {
    Instant fixedInstant = Instant.parse("2025-03-17T10:00:00Z");
    ZoneId utc = ZoneId.of("UTC");
    Clock fixedClock = Clock.fixed(fixedInstant, utc);

    Optional<MeasureSetActionLog> measureSetActionLog =
        Optional.of(
            MeasureSetActionLog.builder()
                .actions(
                    List.of(
                        AccessControlAction.builder()
                            .sharedWith("sharedWith")
                            .actionType(ActionType.SHARED)
                            .performedAt(fixedClock.instant())
                            .performedBy("performedByUserId")
                            .build()))
                .build());

    when(measureSetActionLogRepository.findByTargetId(anyString())).thenReturn(measureSetActionLog);

    MeasureSetActionLog result = actionLogService.findMeasureSetActionLogByTargetId("TARGET_ID");

    verify(measureSetActionLogRepository, times(1))
        .findByTargetId(targetIdArgumentCaptor.capture());

    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    assertThat(result.getActions().get(0).getSharedWith(), is(equalTo("sharedWith")));
    assertThat(result.getActions().get(0).getActionType(), is(equalTo(ActionType.SHARED)));
    assertThat(result.getActions().get(0).getPerformedAt(), is(equalTo(fixedClock.instant())));
    assertThat(result.getActions().get(0).getPerformedBy(), is(equalTo("performedByUserId")));
  }

  @Test
  void testFindMeasureSetActionLogByTargetIdReturnsNull() {
    Optional<MeasureSetActionLog> measureSetActionLog = Optional.empty();

    when(measureSetActionLogRepository.findByTargetId(anyString())).thenReturn(measureSetActionLog);

    MeasureSetActionLog result = actionLogService.findMeasureSetActionLogByTargetId("TARGET_ID");

    verify(measureSetActionLogRepository, times(1))
        .findByTargetId(targetIdArgumentCaptor.capture());

    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    assertThat(result, is(equalTo(null)));
  }
}
