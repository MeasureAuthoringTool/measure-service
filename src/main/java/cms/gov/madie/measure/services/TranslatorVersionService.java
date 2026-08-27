package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.repositories.MeasureRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslatorVersionService {

  private final ElmTranslatorClient elmTranslatorClient;
  private final MeasureRepository measureRepository;

  /**
   * Enriches each MeasureListDTO with the appropriate translator version. For draft measures, the
   * latest translator version is fetched. For versioned measures, the translator version is
   * extracted from the stored ELM JSON.
   *
   * @param measureList the list of measures to enrich
   */
  public void enrichWithTranslatorVersion(List<MeasureListDTO> measureList) {
    if (CollectionUtils.isEmpty(measureList)) {
      return;
    }
    measureList.forEach(
        measure -> {
          // for draft measures, always show the latest translator version
          if (measure.getMeasureMetaData().isDraft()) {
            String latestTranslatorVersion =
                elmTranslatorClient.getCqlToElmTranslatorVersion(measure.getModel());
            measure.setTranslatorVersion(latestTranslatorVersion);
          } else {
            // for versioned measures, show the translator version that was used to generate the ELM
            measureRepository
                .findById(measure.getId())
                .ifPresent(
                    m -> {
                      String version =
                          elmTranslatorClient.getTranslatorVersionFromElmJson(m.getElmJson());
                      measure.setTranslatorVersion(version);
                    });
          }
        });
  }
}
