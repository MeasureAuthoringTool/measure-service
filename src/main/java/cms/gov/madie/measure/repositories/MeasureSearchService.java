package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.LibraryUsage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MeasureSearchService {
  /**
   * @param userId - current user
   * @param pageable - instance of Pageable
   * @param ownershipTypes
   * @return Pageable List of measures that are active based on searchCriteria
   */
  Page<MeasureListDTO> searchMeasuresByCriteria(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria searchCriteria,
      List<OwnershipType> ownershipTypes);

  /**
   * Get the active measures that are currently under review (review status of Ready, In Progress or
   * Complete), filtered by the given search criteria.
   *
   * @param userId - current user, used to filter out locks held by the user themselves
   * @param pageable - instance of Pageable
   * @param searchCriteria - search criteria, may be null
   * @return Pageable list of measures under review
   */
  Page<MeasureListDTO> searchMeasuresInReview(
      String userId, Pageable pageable, MeasureSearchCriteria searchCriteria);

  /**
   * Get all the measures(name, version and owner) if they include any version of given library name
   *
   * @param name -> library name for which usage needs to be determined
   * @return List<LibraryUsage> -> LibraryUsage: name, version and owner of including library
   */
  List<LibraryUsage> findLibraryUsageByLibraryName(String name);

  int countMeasuresByOwnership(boolean isActive, String userId, List<OwnershipType> ownershipTypes);

  int countMeasuresByReview(boolean isActive, String userId, List<OwnershipType> ownershipTypes);
}
