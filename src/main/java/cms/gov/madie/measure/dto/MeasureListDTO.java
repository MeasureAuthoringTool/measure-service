package cms.gov.madie.measure.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.*;
import gov.cms.madie.models.utils.VersionJsonSerializer;
import gov.cms.madie.models.validators.EnumValidator;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import java.time.Instant;

@Data
@Document
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "model",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = FhirMeasure.class, name = "QI-Core v4.1.1"),
  @JsonSubTypes.Type(value = QdmMeasure.class, name = "QDM v5.6")
})
public class MeasureListDTO {

  private String id;
  private String measureSetId;
  private String measureName;

  @JsonSerialize(using = VersionJsonSerializer.VersionSerializer.class)
  @JsonDeserialize(using = VersionJsonSerializer.VersionDeserializer.class)
  private Version version;

  @NotBlank(message = "Model is required")
  @EnumValidator(
      enumClass = ModelType.class,
      message = "MADiE was unable to complete your request, please try again.",
      groups = {Measure.ValidationOrder5.class})
  private String model;

  @DocumentReference private MeasureSet measureSet;

  private boolean active;
  private String ecqmTitle;
  private Instant lastModifiedAt;

  private MeasureMetaData measureMetaData;
  private boolean hasAssociatedMeasures;
}
