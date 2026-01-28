package cms.gov.madie.measure.repositories;

import com.mongodb.client.result.UpdateResult;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionLog;
import gov.cms.madie.models.measure.TestCase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

@ExtendWith(MockitoExtension.class)
@EnableMongoRepositories(basePackages = "com.gov.madie.measure.repository")
class ActionLogRepositoryImplTest {

  @Mock MongoTemplate mongoTemplate;

  @InjectMocks ActionLogRepositoryImpl actionLogRepository;

  @Test
  void returnsFalseForNullTargetId() {
    boolean output = actionLogRepository.pushEvent(null, Action.builder().build(), "COL1");
    assertThat(output, is(false));
  }

  @Test
  void returnsFalseForEmptyTargetId() {
    boolean output = actionLogRepository.pushEvent("", Action.builder().build(), "COL1");
    assertThat(output, is(false));
  }

  @Test
  void returnsFalseForNullAction() {
    boolean output = actionLogRepository.pushEvent("TARGET_ID", null, "COL1");
    assertThat(output, is(false));
  }

  @Test
  void returnsTrueForValidInputs() {
    when(mongoTemplate.upsert(any(Query.class), any(Update.class), anyString()))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    boolean output = actionLogRepository.pushEvent("TARGET_ID", Action.builder().build(), "COL1");
    assertThat(output, is(true));
  }

  @Test
  void returnsFalseForValidInputsNoUpsert() {
    when(mongoTemplate.upsert(any(Query.class), any(Update.class), anyString()))
        .thenReturn(UpdateResult.acknowledged(1, 0L, null));
    boolean output = actionLogRepository.pushEvent("TARGET_ID", Action.builder().build(), "COL1");
    assertThat(output, is(false));
  }

  @Test
  void testFindAllActionLogs() {
    actionLogRepository.findAllActionLogs(TestCase.class);
    verify(mongoTemplate).findAll(ActionLog.class, "testCaseActionLog");
  }

  @Test
  void testSaveAllActionLogs() {
    ActionLog actionLog =
        ActionLog.builder().id("testActionLogId").targetId("testTargetId").build();
    actionLogRepository.saveAllActionLogs(List.of(actionLog), TestCase.class);
    verify(mongoTemplate).insert(anyList(), eq("testCaseActionLog"));
  }

  @Test
  void testRemovesActionsAndDeletesEmptyLogs() {
    UpdateResult updateResult = UpdateResult.acknowledged(2, 2L, null);
    when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), anyString()))
        .thenReturn(updateResult);

    actionLogRepository.removeActionsByUsers(
        Arrays.asList("testUser1", "testUser2"), TestCase.class);

    // Verify updateMulti called
    verify(mongoTemplate)
        .updateMulti(
            argThat(q -> q.getQueryObject().toString().contains("actions.performedBy")),
            any(Update.class),
            eq("testCaseActionLog"));

    // Verify remove called for empty actions
    verify(mongoTemplate)
        .remove(
            argThat(
                q ->
                    q.getQueryObject().toString().contains("actions")
                        && q.getQueryObject().toString().contains("$size")),
            eq("testCaseActionLog"));
  }

  @Test
  void testUpdateAllActionLogs() {
    ActionLog actionLog =
        ActionLog.builder().id("testActionLogId").targetId("testTargetId").build();
    actionLogRepository.updateAllActionLogs(List.of(actionLog), TestCase.class);
    verify(mongoTemplate).save(actionLog, "testCaseActionLog");
  }
}
