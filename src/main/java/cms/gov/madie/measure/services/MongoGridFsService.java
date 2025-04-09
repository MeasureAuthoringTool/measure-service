package cms.gov.madie.measure.services;

import com.mongodb.MongoGridFSException;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.AllArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@AllArgsConstructor
public class MongoGridFsService {
  private final GridFsOperations operations;

  public String findById(String gridFsId) {
    if (gridFsId == null || gridFsId.isEmpty()) {
      return null;
    }
    GridFSFile file =
        operations.findOne(Query.query(Criteria.where("_id").is(new ObjectId(gridFsId))));
    if (file == null) {
      return null;
    }
    GridFsResource resource = operations.getResource(file);
    try (InputStream inputStream = resource.getInputStream()) {
      return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new MongoGridFSException("Failed to read GridFS content for ID: " + gridFsId, e);
    }
  }

  public ObjectId save(ByteArrayInputStream inputStream, String filename, String contentType) {
    return operations.store(inputStream, filename, contentType);
  }
}
