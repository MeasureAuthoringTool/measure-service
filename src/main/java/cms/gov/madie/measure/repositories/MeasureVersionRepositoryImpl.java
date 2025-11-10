package cms.gov.madie.measure.repositories;

import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.common.Version;

@Repository
public class MeasureVersionRepositoryImpl implements MeasureVersionRepository {

  private final MongoTemplate mongoTemplate;

  public MeasureVersionRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Optional<Version> findMaxVersionByMeasureSetId(String measureSetId) {
    Query q =
        new Query(Criteria.where("measureSetId").is(measureSetId).and("active").is(true))
            .with(
                Sort.by(
                    Sort.Direction.DESC,
                    "version.major",
                    "version.minor",
                    "version.revisionNumber"))
            .limit(1);
    Measure one = mongoTemplate.findOne(q, Measure.class);

    if (one == null || one.getVersion() == null) {
      return Optional.empty();
    } else {
      return Optional.of(one.getVersion());
    }
  }

  @Override
  public Optional<Version> findMaxMinorVersionByMeasureSetIdAndVersionMajor(
      String measureSetId, int majorVersion) {
    Query q =
        new Query(
                Criteria.where("measureSetId")
                    .is(measureSetId)
                    .and("version.major")
                    .is(majorVersion)
                    .and("active")
                    .is(true))
            .with(
                Sort.by(
                    Sort.Direction.DESC,
                    "version.major",
                    "version.minor",
                    "version.revisionNumber"))
            .limit(1);
    Measure one = mongoTemplate.findOne(q, Measure.class);

    if (one == null || one.getVersion() == null) {
      return Optional.empty();
    } else {
      return Optional.of(one.getVersion());
    }
  }

  @Override
  public Optional<Version> findMaxRevisionNumberByMeasureSetIdAndVersionMajorAndMinor(
      String measureSetId, int majorVersion, int minorVersion) {

    Query q =
        new Query(
                Criteria.where("measureSetId")
                    .is(measureSetId)
                    .and("version.major")
                    .is(majorVersion)
                    .and("version.minor")
                    .is(minorVersion)
                    .and("active")
                    .is(true))
            .with(
                Sort.by(
                    Sort.Direction.DESC,
                    "version.major",
                    "version.minor",
                    "version.revisionNumber"))
            .limit(1);
    Measure one = mongoTemplate.findOne(q, Measure.class);

    if (one == null || one.getVersion() == null) {
      return Optional.empty();
    } else {
      return Optional.of(one.getVersion());
    }
  }
}
