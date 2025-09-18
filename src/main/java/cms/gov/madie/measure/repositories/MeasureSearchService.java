package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureListDTO;

import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.LibraryUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

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

  Page<MeasureListDTO> searchMeasuresByCriteriaWhenFeatureFlagIsOff(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria searchCriteria,
      List<OwnershipType> ownershipTypes);

  Page<MeasureListDTO> updatedSearchMeasuresByCriteria(
      String userId,
      Pageable pageable,
      MeasureSearchCriteria measureSearchCriteria,
      List<OwnershipType> ownershipTypes);

  /**
   * Get all the measures(name, version and owner) if they include any version of given library name
   *
   * @param name -> library name for which usage needs to be determined
   * @return List<LibraryUsage> -> LibraryUsage: name, version and owner of including library
   */
  List<LibraryUsage> findLibraryUsageByLibraryName(String name);

  int countMeasuresByOwnership(boolean isActive, String userId, List<OwnershipType> ownershipTypes);
}
