package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.exceptions.InternalServerException;
import gov.cms.madie.models.measure.FhirMeasure;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.QdmMeasure;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Repository
public class MeasurePatchRepositoryImpl implements MeasurePatchRepository {
  private final MongoOperations mongoOperations;

  public MeasurePatchRepositoryImpl(MongoOperations mongoOperations) {
    this.mongoOperations = mongoOperations;
  }

  @Override
  public Measure findAndModify(Measure updatedMeasure) {
    List<String> excludedFields = Arrays.asList("testCases", "testCaseConfiguration", "measureSet", "elmXml");
    return findAndModify(updatedMeasure, excludedFields);
  }

  @Override
  public Measure findAndModify(Measure updatedMeasure, List<String> excludedFields) {
    Update patchUpdate = new Update();

    if (updatedMeasure instanceof QdmMeasure) {
      for (Field field : QdmMeasure.class.getDeclaredFields()) {
        addFieldToUpdate(updatedMeasure, excludedFields, field, patchUpdate);
      }
    } else if (updatedMeasure instanceof FhirMeasure) {
      for (Field field : FhirMeasure.class.getDeclaredFields()) {
        addFieldToUpdate(updatedMeasure, excludedFields, field, patchUpdate);
      }
    }

    for (Field field : Measure.class.getDeclaredFields()) {
      addFieldToUpdate(updatedMeasure, excludedFields, field, patchUpdate);
    }

    return mongoOperations
      .update(Measure.class)
      .matching(query(where("_id").is(updatedMeasure.getId())))
      .apply(patchUpdate)
      .withOptions(FindAndModifyOptions.options().returnNew(true))
      .findAndModifyValue();
  }

  private void addFieldToUpdate(
    Measure updatedMeasure, List<String> excludedFields, Field field, Update patchUpdate) {
    if (!excludedFields.contains(field.getName())) {
      field.setAccessible(true); // Allow access to private fields
      try {
        patchUpdate.set(field.getName(), field.get(updatedMeasure));
      } catch (IllegalAccessException e) {
        throw new InternalServerException(
          "Failed to access Measure field during findAndModify Set: " + field.getName());
      }
    }
  }
}
