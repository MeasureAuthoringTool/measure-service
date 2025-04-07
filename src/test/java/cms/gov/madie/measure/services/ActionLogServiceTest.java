package cms.gov.madie.measure.services;

import cms.gov.madie.measure.repositories.MeasureActionLogRepository;
import cms.gov.madie.measure.repositories.MeasureSetActionLogRepository;
import cms.gov.madie.measure.utils.ActionLogCollectionType;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import gov.cms.madie.models.measure.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionLogServiceTest {

  @Mock MeasureActionLogRepository measureActionLogRepository;
  @Mock MeasureSetActionLogRepository measureSetActionLogRepository;
  @InjectMocks ActionLogService actionLogService;

  @Captor private ArgumentCaptor<Action> actionArgumentCaptor;
  @Captor private ArgumentCaptor<String> stringArgumentCaptor;
  @Captor private ArgumentCaptor<String> collectionArgumentCaptor;

  @Test
  void testLogActionReturnsTrue() {
    when(measureActionLogRepository.pushEvent(anyString(), any(Action.class), anyString()))
        .thenReturn(true);
    boolean output =
        actionLogService.logAction("TARGET_ID", TestCase.class, ActionType.CREATED, "firstUser");
    assertThat(output, is(true));
    verify(measureActionLogRepository, times(1))
        .pushEvent(
            stringArgumentCaptor.capture(),
            actionArgumentCaptor.capture(),
            collectionArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    Action value = actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.CREATED)));
    assertThat(value.getPerformedBy(), is(equalTo("firstUser")));
    assertThat(
        collectionArgumentCaptor.getValue(),
        is(equalTo(ActionLogCollectionType.TESTCASE.getCollectionName())));
  }

  @Test
  void testLogActionReturnsFalse() {
    when(measureActionLogRepository.pushEvent(anyString(), any(Action.class), anyString()))
        .thenReturn(false);
    boolean output =
        actionLogService.logAction(
            "TARGET_ID", Measure.class, ActionType.VERSIONED_MAJOR, "secondUser");
    assertThat(output, is(false));
    verify(measureActionLogRepository, times(1))
        .pushEvent(
            stringArgumentCaptor.capture(),
            actionArgumentCaptor.capture(),
            collectionArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    Action value = actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.VERSIONED_MAJOR)));
    assertThat(value.getPerformedBy(), is(equalTo("secondUser")));
    assertThat(
        collectionArgumentCaptor.getValue(),
        is(equalTo(ActionLogCollectionType.MEASURE.getCollectionName())));
  }

  @Test
  void testLogMeasureSetActionReturnsTrue() {
    when(measureSetActionLogRepository.pushEvent(anyString(), any(Action.class), anyString()))
        .thenReturn(true);
    boolean output =
        actionLogService.logMeasureSetAction(
            "TARGET_ID", MeasureSet.class, ActionType.CREATED, "firstUser");
    assertThat(output, is(true));
    verify(measureSetActionLogRepository, times(1))
        .pushEvent(
            stringArgumentCaptor.capture(),
            actionArgumentCaptor.capture(),
            collectionArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    Action value = actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.CREATED)));
    assertThat(value.getPerformedBy(), is(equalTo("firstUser")));
    assertThat(
        collectionArgumentCaptor.getValue(),
        is(equalTo(ActionLogCollectionType.MEASURESET.getCollectionName())));
  }

  @Test
  void testLogMeasureSetActionReturnsFalse() {
    when(measureSetActionLogRepository.pushEvent(anyString(), any(Action.class), anyString()))
        .thenReturn(false);
    boolean output =
        actionLogService.logMeasureSetAction(
            "TARGET_ID", MeasureSet.class, ActionType.ASSOCIATED, "secondUser");
    assertThat(output, is(false));
    verify(measureSetActionLogRepository, times(1))
        .pushEvent(
            stringArgumentCaptor.capture(),
            actionArgumentCaptor.capture(),
            collectionArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    Action value = actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.ASSOCIATED)));
    assertThat(value.getPerformedBy(), is(equalTo("secondUser")));
    assertThat(
        collectionArgumentCaptor.getValue(),
        is(equalTo(ActionLogCollectionType.MEASURESET.getCollectionName())));
  }
}
