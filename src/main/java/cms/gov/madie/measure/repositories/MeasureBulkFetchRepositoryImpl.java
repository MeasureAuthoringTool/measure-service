package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureListDTO;
import gov.cms.madie.models.measure.Measure;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Implementation of MeasureBulkFetchRepository using MongoDB aggregation pipelines. Avoids N+1
 * query problems by using $lookup to join measure and measure_set collections.
 */
@Repository
@RequiredArgsConstructor
public class MeasureBulkFetchRepositoryImpl implements MeasureBulkFetchRepository {

  private final MongoTemplate mongoTemplate;

  @Override
  public List<MeasureListDTO> findAllByIdInWithMeasureSet(Collection<String> measureIds) {
    if (CollectionUtils.isEmpty(measureIds)) {
      return Collections.emptyList();
    }

    List<String> uniqueIds = measureIds.stream().filter(Objects::nonNull).distinct().toList();

    if (CollectionUtils.isEmpty(uniqueIds)) {
      return List.of();
    }

    // Convert String IDs to ObjectId instances for MongoDB aggregation
    List<ObjectId> objectIds = uniqueIds.stream().map(ObjectId::new).toList();

    // Stage 1: Match measures by IDs
    MatchOperation matchOperation = match(Criteria.where("_id").in(objectIds));

    // Stage 2: Lookup (join) with measure_set collection
    LookupOperation lookupOperation =
        LookupOperation.newLookup()
            .from("measureSet")
            .localField("measureSetId")
            .foreignField("measureSetId")
            .as("measureSet");

    // Stage 3: Unwind the measureSet array to get a single object
    UnwindOperation unwindOperation = unwind("measureSet");

    // Stage 4: Project to MeasureListDTO
    ProjectionOperation projectionOperation = project(MeasureListDTO.class);

    // Build and execute aggregation
    Aggregation aggregation =
        newAggregation(matchOperation, lookupOperation, unwindOperation, projectionOperation);

    return mongoTemplate
        .aggregate(aggregation, Measure.class, MeasureListDTO.class)
        .getMappedResults();
  }
}
