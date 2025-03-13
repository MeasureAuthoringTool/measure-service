package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.MeasureUtil;
import gov.cms.madie.models.common.IncludedLibrary;
import gov.cms.madie.models.measure.Measure;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

@Slf4j
@ChangeUnit(id = "add_included_libraries_prop", order = "1", author = "madie_dev")
public class AddIncludedLibrariesPropChangeUnit {

  @Execution
  public void addIncludedLibrariesProp(MeasureRepository measureRepository) {
    log.info("Running changelog to update included libraries");
    List<Measure> measures = measureRepository.findAll();
    if (CollectionUtils.isNotEmpty(measures)) {
      List<Measure> updatedMeasures =
          measures.stream()
              .map(
                  measure -> {
                    List<IncludedLibrary> includedLibraries =
                        MeasureUtil.getIncludedLibraries(measure.getCql());
                    measure.setIncludedLibraries(includedLibraries);
                    return measure;
                  })
              .toList();
      measureRepository.saveAll(updatedMeasures);
    }
    log.info("Running changelog to update included libraries is complete");
  }

  @RollbackExecution
  public void rollbackExecution(MongoTemplate mongoTemplate) {
    log.info("Rolling back included libraries update changelog");
    Query query = new Query();
    Update update = new Update().unset("includedLibraries");
    BulkOperations bulkOperations =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Measure.class);
    bulkOperations.updateMulti(query, update);
    bulkOperations.execute();
    log.info("Rollback included libraries update changelog complete");
  }
}
