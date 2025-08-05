package cms.gov.madie.measure.dto;

import gov.cms.madie.models.measure.MeasureDefinition;

public record MeasureDefinitionDTO(String id, MeasureDefinition measureDefinition) implements MeasureField {

  @Override
  public String getField() {
    return "definition";
  }
}
