package cms.gov.madie.measure.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import cms.gov.madie.measure.dto.MeasureListDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import gov.cms.madie.models.measure.Measure;

public interface MeasureRepository
    extends MongoRepository<Measure, String>,
        MeasureVersionRepository,
        MeasureSearchService,
        MeasureCmsIdRepository,
        TestCaseRepository,
        MeasurePatchRepository {
  @Query("{cqlLibraryName : ?0, active : true}")
  List<Measure> findAllByCqlLibraryName(String cqlLibraryName);

  Optional<Measure> findByIdAndActive(String id, Boolean active);

  Page<MeasureListDTO> findAllByActive(Boolean active, Pageable page);

  @Aggregation(
      pipeline = {
        "{'$group': {'_id': '$measureSetId',"
            + "'measureSetId': {'$first':'$measureSetId'},"
            + "'createdBy': {'$first':'$createdBy'}}}",
        "{'$sort': {'createdAt':1}}"
      })
  List<Measure> findOldestMeasureSet();

  @Query(value = "{_id: ?0}", fields = "{'testCases.series': 1, _id: 0}")
  Optional<Measure> findAllTestCaseSeriesByMeasureId(String measureId);

  @Query(value = "{active : true}", fields = "{_id: 1, model: 1}")
  List<Measure> findAllMeasureIdsByActive();

  @Query(value = "{active : true, 'measureMetaData.draft': ?0}", fields = "{_id: 1, model: 1}")
  List<Measure> findAllMeasureIdsByActiveAndMeasureMetaDataDraft(boolean draft);

  @Query(value = "{'groups._id': ?0}")
  Optional<Measure> findGroupById(String groupId);

  boolean existsByMeasureSetIdAndActiveAndMeasureMetaDataDraft(
      String setId, boolean active, boolean draft);

  // Map measureSetId, boolean (ie.,
  // id 1 - drafted , 2 - versioned  setId 4 4, false
  // id 1 - versioned , 2 - versioned setId 4 4, true
  List<Measure> findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(
      List<String> setIds, boolean active, boolean draft);

  List<Measure> findAllByModel(String model);

  List<Measure> findAllByMeasureSetIdAndActive(String measureSetId, boolean isActive);

  int countAllByActiveAndMeasureSetContaining(boolean isActive, String userId);

  List<Measure> findByMeasureSetIdAndModelInAndMeasureMetaDataDraft(
      String setId, Collection<String> model, boolean draft);
}
