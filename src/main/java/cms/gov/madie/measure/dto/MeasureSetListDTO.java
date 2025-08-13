package cms.gov.madie.measure.dto;

import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.*;
import gov.cms.madie.models.utils.VersionJsonSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.ArrayList;

@Data
@Document
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class MeasureSetListDTO {

  private String id;
  private String measureSetId;
  private String owner;
  private String measureName;
  private String cmsId;

  private ArrayList<Measure> measures;

  private Instant lastModifiedAt;
  private boolean hasAssociatedMeasures;
  private MeasureMetaData measureMetaData;

  @JsonSerialize(using = VersionJsonSerializer.VersionSerializer.class)
  @JsonDeserialize(using = VersionJsonSerializer.VersionDeserializer.class)
  private Version version;
}
