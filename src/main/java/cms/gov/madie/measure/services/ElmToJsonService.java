package cms.gov.madie.measure.services;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import cms.gov.madie.measure.exceptions.CqlElmTranslationErrorException;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import gov.cms.madie.models.measure.ElmJson;
import gov.cms.madie.models.measure.Measure;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
@Service
public class ElmToJsonService {
  private final ElmTranslatorClient elmTranslatorClient;

  protected void retrieveElmJson(Measure measure, String elmErrorSeverity) {
    if (StringUtils.isBlank(measure.getCql())) {
      throw new InvalidResourceStateException(
          "Measure", measure.getId(), "since there is no associated CQL.");
    }

    if (measure.isCqlErrors()) {
      throw new InvalidResourceStateException(
          "Measure", measure.getId(), "since CQL errors exist.");
    }

    if (CollectionUtils.isEmpty(measure.getGroups())) {
      throw new InvalidResourceStateException(
          "Measure", measure.getId(), "since there are no associated population criteria.");
    }

    final ElmJson elm =
        elmTranslatorClient.getElmJson(measure.getCql(), measure.getModel(), elmErrorSeverity);
    if (elmTranslatorClient.hasErrors(elm)) {
      throw new CqlElmTranslationErrorException(measure.getMeasureName());
    }
    measure.setElmJson(elm.getJson());
    measure.setElmXml(elm.getXml());
  }
}
