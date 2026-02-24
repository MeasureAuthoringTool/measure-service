package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.RichTextUtil;
import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoOperations;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Change unit to perform a one-time migration of text fields in the Measure model to HTML format in
 * preparation for the Rich Text Editor. This migration is necessary to ensure that existing text
 * data is compatible with the new Rich Text Editor.
 */
@Slf4j
@ChangeUnit(id = "htmlify_text_data", order = "1", author = "madie_dev")
public class HtmlifyTextData {

  private final List<Measure> originalMeasures = new ArrayList<>();

  @Execution
  public void htmlfiyText(MeasureRepository measureRepository, MongoOperations mongoOperations) {
    // Convert text fields to HTML
    List<Measure> draftActiveMeasures =
        mongoOperations.find(
            query(where("active").is(true).and("measureMetaData.draft").is(true)), Measure.class);
    for (Measure measure : draftActiveMeasures) {
      originalMeasures.add(measure);
      Measure msr = measure.deepCopy();
      // MetaData fields
      RichTextUtil.htmlifyMeasureRichTextContents(msr);
      measureRepository.findAndModify(
          msr,
          List.of(
              "measureSetId",
              "measureSet",
              "elmXml",
              "elmJson",
              "cql",
              "testCases",
              "testCaseConfiguration",
              "lastModifiedAt",
              "lastModifiedBy"));
    }
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) {
    log.info("Rolling back htmlify text data changelog");
    if (CollectionUtils.isNotEmpty(originalMeasures)) {
      measureRepository.saveAll(originalMeasures);
    }
    log.info("Rollback htmlify text data changelog complete");
  }
}
