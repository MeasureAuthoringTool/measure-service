package cms.gov.madie.measure.services;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.AllArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@AllArgsConstructor
public class MongoGridFsService {
  private final GridFsOperations operations;

  public String findById(String gridFsId) {
    if (gridFsId == null || gridFsId.isEmpty()) {
      return null;
    }
    Optional<GridFSFile> file =
        Optional.ofNullable(
            operations.findOne(Query.query(Criteria.where("_id").is(new ObjectId(gridFsId)))));
    if (file.isPresent()) {
      GridFsResource resource = operations.getResource(file.get());
      try (InputStream inputStream = resource.getInputStream()) {
        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeException("Failed to read GridFS content for ID: " + gridFsId, e);
      }
    }
    return null;
  }
}
