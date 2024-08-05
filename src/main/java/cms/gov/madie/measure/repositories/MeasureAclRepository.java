package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.LibraryUsage;
import cms.gov.madie.measure.dto.MeasureListDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MeasureAclRepository {
  /**
   * Measure is considered to be my measure if provided user is the owner of this measure or is
   * shared with provided user and measure is active(measure.active = true)
   *
   * @param userId- current user
   * @param pageable- instance of Pageable
   * @return Pageable List of measures
   */
  Page<MeasureListDTO> findMyActiveMeasures(String userId, Pageable pageable, String searchTerm);

  /**
   * Get all the libraries(name, version and owner) if they include library with given library name,
   * version doesn't matter
   *
   * @param name -> library name for which usage needs to be determined
   * @return List<LibraryUsage> -> LibraryUsage: name, version and owner of including library
   */
  List<LibraryUsage> findLibraryUsageByLibraryName(String name);
}
