package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureDefinitionDTO;
import cms.gov.madie.measure.dto.MeasureField;
import cms.gov.madie.measure.dto.MeasureMetadataDTO;
import gov.cms.madie.models.measure.Measure;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Objects;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

@Repository
public class MeasurePatchRepositoryImpl implements MeasurePatchRepository {
  private final MongoOperations mongoOperations;

  public MeasurePatchRepositoryImpl(MongoOperations mongoOperations) {
    this.mongoOperations = mongoOperations;
  }

  @Override
  public Measure partialUpdate(String measureId, MeasureField update) {
    if (measureId == null || measureId.isBlank()) {
      throw new IllegalArgumentException("Measure ID must not be null or blank");
    }

    if (Objects.requireNonNull(update) instanceof MeasureMetadataDTO metadataDTO) {
      if (metadataDTO.measureMetaData() == null) {
        throw new IllegalArgumentException("Measure metadata must not be null");
      }
      return mongoOperations
        .update(Measure.class)
        .matching(query(where("_id").is(measureId)))
        .apply(
          update(
            metadataDTO.getField(), metadataDTO.measureMetaData()))
        .withOptions(FindAndModifyOptions.options().returnNew(true))
        .findAndModifyValue();
    } else if(update instanceof MeasureDefinitionDTO definitionDTO) {
      if (definitionDTO.measureDefinition() == null) {
        throw new IllegalArgumentException("Measure definition must not be null");
      }
      return mongoOperations
        .update(Measure.class)
        .matching(query(where("_id").is(measureId)))
        .apply(
          update(
            definitionDTO.getField(), definitionDTO.measureDefinition()))
        .withOptions(FindAndModifyOptions.options().returnNew(true))
        .findAndModifyValue();
    }

    throw new IllegalArgumentException("Unsupported update type: " + update);
  }

  @Override
  public Measure patchMeasure(Measure measure) {
    Update patchUpdate = new Update();
    patchUpdate.set("measure", measure);
    return mongoOperations
      .update(Measure.class)
      .matching(query(where("_id").is(measure.getId())))
      .apply(patchUpdate)
      .withOptions(FindAndModifyOptions.options().returnNew(true))
      .findAndModifyValue();
  }
}
