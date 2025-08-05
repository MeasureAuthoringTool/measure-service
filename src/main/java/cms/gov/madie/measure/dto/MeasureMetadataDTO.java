package cms.gov.madie.measure.dto;

import gov.cms.madie.models.measure.MeasureMetaData;
import jakarta.validation.Valid;

public record MeasureMetadataDTO(String id, @Valid MeasureMetaData measureMetaData) implements MeasureField {

  public String getField() {
    return "measureMetaData";
  }
}
