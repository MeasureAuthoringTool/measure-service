package cms.gov.madie.measure.dto.Comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reviewComments")
public class ReviewComment {

  @Id private String id;

  private String commentId;

  private CommentType commentType;

  @Indexed private String measureId;

  private int lineNumber;

  private String lineContent;

  private String author;

  private String text;

  private Instant createdAt;

  @Builder.Default private List<CommentReply> replies = new ArrayList<>();

  private boolean resolved;
}
