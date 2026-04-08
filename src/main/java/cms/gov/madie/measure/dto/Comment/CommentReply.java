package cms.gov.madie.measure.dto.Comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentReply {

  private String id;

  private String author;

  private String text;

  private Instant createdAt;
}
