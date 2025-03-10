package cms.gov.madie.measure.services;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class GridFsService {
  private final GridFsTemplate gridFsTemplate;

  public GridFsService(GridFsTemplate gridFsTemplate) {
    this.gridFsTemplate = gridFsTemplate;
  }

  public String fetchGridFsContent(String gridFsId) {
    if (gridFsId == null || gridFsId.isEmpty()) {
      return null;
    }
    GridFSFile gridFsFile =
        gridFsTemplate.findOne(new Query(Criteria.where("_id").is(new ObjectId(gridFsId))));
    try {
      GridFsResource resource = gridFsTemplate.getResource(gridFsFile);
      return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read GridFS content for ID: " + gridFsId, e);
    }
  }
}
