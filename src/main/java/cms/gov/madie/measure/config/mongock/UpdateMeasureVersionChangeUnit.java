package cms.gov.madie.measure.config.mongock;

import java.util.List;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "measure_version_update", order = "1", author = "madie_dev")
public class UpdateMeasureVersionChangeUnit {

  @Execution
  public void updateMeasureVersion(MeasureRepository measureRepository) throws Exception {
    log.debug("Entering updateMeasureVersion()");

    List<Measure> measureList = measureRepository.findAll();
    measureList.stream()
        .forEach(
            measure -> {
              if (measure.getVersion() != null) {
                // only MAT measure could have actual version number
                if (!"0.0.000".equalsIgnoreCase(measure.getVersion().toString())) {
                  log.info(
                      "updateMeasureVersion(): measure id = "
                          + measure.getId()
                          + " measure cms id = "
                          + " version = "
                          + measure.getVersion().toString()
                          + " revision number = "
                          + measure.getRevisionNumber());
                }
                measure
                    .getVersion()
                    .setRevisionNumber(
                        measure.getRevisionNumber() != null
                            ? Integer.valueOf(measure.getRevisionNumber())
                            : measure.getVersion().getRevisionNumber());
              } else {
                measure.setVersion(Version.builder().major(0).minor(0).revisionNumber(0).build());
              }
              if (measure.getMeasureMetaData() != null) {
                measure.getMeasureMetaData().setDraft(true);
              } else {
                MeasureMetaData metaData = new MeasureMetaData();
                metaData.setDraft(true);
                measure.setMeasureMetaData(metaData);
              }
              measure.setRevisionNumber(null);
              measureRepository.save(measure);
            });
  }

  @RollbackExecution
  public void rollbackExecution(MeasureRepository measureRepository) throws Exception {
    log.debug("Entering rollbackExecution()");

    List<Measure> measureList = measureRepository.findAll();
    measureList.stream()
        .forEach(
            measure -> {
              if (measure.getVersion() != null) {
                // only MAT measure could have actual version number
                if (!"0.0.000".equalsIgnoreCase(measure.getVersion().toString())) {
                  log.info(
                      "rollbackExecution(): measure id = "
                          + measure.getId()
                          + " measure cms id = "
                          + " version = "
                          + measure.getVersion().toString());
                }
                measure.setRevisionNumber(
                    String.valueOf(measure.getVersion().getMajor())
                        + "."
                        + String.valueOf(measure.getVersion().getMinor())
                        + "."
                        + String.format("%03d", measure.getVersion().getRevisionNumber()));

              } else {
                measure.setRevisionNumber("0.0.000");
              }
              if (measure.getMeasureMetaData() != null) {
                measure.getMeasureMetaData().setDraft(true);
              } else {
                MeasureMetaData metaData = new MeasureMetaData();
                metaData.setDraft(true);
                measure.setMeasureMetaData(metaData);
              }
              measureRepository.save(measure);
            });
  }
}
