package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.UUID;

@Slf4j
@ChangeUnit(id = "add_test_case_set_ids", order = "2", author = "madie_dev")
public class AddTestCaseSetIdsChangeUnit {

  static final int PAGE_SIZE = 50;

  @Execution
  public void addTestCaseSetIds(MongoTemplate mongoTemplate, MeasureRepository  measureRepository) {
    log.info("Starting changelog to add test case set ids");

    int skip = 0;
    int totalProcessed = 0;
    int totalSkipped = 0;

    List<Measure> measures;
    do {
      // Step 1: Aggregation to get one measure (draft or latest versioned) per measureSetId
      // for active, non-QDM measures with test cases, paginated via $skip/$limit
      measures = getDraftOrLatestVersionedMeasures(mongoTemplate, skip);
      if (CollectionUtils.isEmpty(measures)) {
        break;
      }

      for (Measure measure : measures) {
        // Step 2: Check if any measure in this set already has testCaseSetId assigned
        if (measureRepository.testCaseSetIdExistsInSet(measure.getMeasureSetId())) {
          totalSkipped++;
          log.debug(
              "Skipping measureSetId [{}] - testCaseSetId already exists in set",
              measure.getMeasureSetId());
          continue;
        }

        if (CollectionUtils.isEmpty(measure.getTestCases())) {
          totalSkipped++;
          continue;
        }

        // Step 3: Assign UUID to each test case and save
        measure.getTestCases().forEach(testCase -> testCase.setTestCaseSetId(UUID.randomUUID()));
        measureRepository.save(measure);
        totalProcessed++;
        log.debug(
            "Assigned testCaseSetIds for measure [{}] in measureSetId [{}]",
            measure.getId(),
            measure.getMeasureSetId());
      }

      skip += PAGE_SIZE;
    } while (measures.size() == PAGE_SIZE);

    log.info(
        "Completed changelog to add test case set ids. Processed: [{}], Skipped: [{}]",
        totalProcessed,
        totalSkipped);
  }

  /**
   * Uses an aggregation pipeline to get one measure per measureSetId: the draft if it exists,
   * otherwise the latest versioned measure. Pipeline stages:
   *
   * <ol>
   *   <li>$match - active, non-QDM measures with non-empty test cases
   *   <li>$sort - by measureMetaData.draft DESC (drafts first), then version DESC (latest first)
   *   <li>$group - by measureSetId, taking $first (the draft or latest versioned measure)
   *   <li>$skip/$limit - for pagination
   * </ol>
   *
   * @param mongoTemplate the MongoTemplate instance
   * @param skip the number of groups to skip (for pagination)
   * @return a list of Measures, one per qualifying measureSetId
   */
  List<Measure> getDraftOrLatestVersionedMeasures(MongoTemplate mongoTemplate, int skip) {
    TypedAggregation<Measure> aggregation =
        Aggregation.newAggregation(
            Measure.class,
            Aggregation.match(
                Criteria.where("active")
                    .is(true)
                    .and("model")
                    .ne(ModelType.QDM_5_6.getValue())
                    .and("testCases")
                    .exists(true)
                    .not()
                    .size(0)),
            Aggregation.sort(
                Sort.by(
                    Sort.Order.desc("measureMetaData.draft"),
                    Sort.Order.desc("version.major"),
                    Sort.Order.desc("version.minor"),
                    Sort.Order.desc("version.revisionNumber"))),
            Aggregation.group("measureSetId").first("$$ROOT").as("doc"),
            Aggregation.replaceRoot("doc"),
            Aggregation.skip(skip),
            Aggregation.limit(PAGE_SIZE));

    AggregationResults<Measure> results = mongoTemplate.aggregate(aggregation, Measure.class);
    return results.getMappedResults();
  }

//  /**
//   * Checks if any measure in the given measureSetId has a test case with a non-null testCaseSetId.
//   *
//   * @param mongoTemplate the MongoTemplate instance
//   * @param measureSetId the measureSetId to check
//   * @return true if at least one test case has a testCaseSetId
//   */
//  boolean testCaseSetIdExistsInSet(MongoTemplate mongoTemplate, String measureSetId) {
//    Query query =
//        new Query(
//            Criteria.where("measureSetId")
//                .is(measureSetId)
//                .and("testCases.testCaseSetId")
//                .exists(true)
//                .ne(null));
//    return mongoTemplate.exists(query, Measure.class);
//  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug("Something went wrong while adding test case set ids.");
  }
}
