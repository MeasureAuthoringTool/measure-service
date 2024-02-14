package cms.gov.madie.measure.services;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import gov.cms.madie.models.measure.Measure;

@Slf4j
@Service
@AllArgsConstructor
public class MeasureTransferService {

  public Measure overwriteExistingMeasure(
      List<Measure> measuresWithSameSetId, Measure transferredMeasure) {
    List<Measure> draftdMeasures =
        measuresWithSameSetId.stream()
            .filter(
                measure ->
                    measure.getMeasureMetaData() != null && measure.getMeasureMetaData().isDraft())
            .collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(draftdMeasures)) {
      draftdMeasures.sort(Comparator.comparing(Measure::getLastModifiedAt));
      Measure mostRecentMeasure = draftdMeasures.get(draftdMeasures.size() - 1);
      transferredMeasure.setId(mostRecentMeasure.getId());
      transferredMeasure.setTestCases(mostRecentMeasure.getTestCases());
      log.info(
          "Overwrite meausre id {} with the testcases from original measure",
          mostRecentMeasure.getId());
    }
    return transferredMeasure;
  }
}