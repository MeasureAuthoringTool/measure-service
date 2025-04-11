package cms.gov.madie.measure.services;

import com.mongodb.MongoGridFSException;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MongoGridFsServiceTest {

  private GridFsOperations gridFsOperations;
  private MongoGridFsService mongoGridFsService;

  @BeforeEach
  void setUp() {
    gridFsOperations = mock(GridFsOperations.class);
    mongoGridFsService = new MongoGridFsService(gridFsOperations);
  }

  @Test
  void findByIdReturnsContent() throws IOException {
    String expectedContent = "Hello, World!";
    String gridFsId = new ObjectId().toString();
    GridFSFile mockFile = mock(GridFSFile.class);
    GridFsResource mockResource = mock(GridFsResource.class);
    InputStream mockStream =
        new ByteArrayInputStream(expectedContent.getBytes(StandardCharsets.UTF_8));

    when(gridFsOperations.findOne(any(Query.class))).thenReturn(mockFile);
    when(gridFsOperations.getResource(mockFile)).thenReturn(mockResource);
    when(mockResource.getInputStream()).thenReturn(mockStream);

    String result = mongoGridFsService.findById(gridFsId);
    assertEquals(expectedContent, result);
  }

  @Test
  void findByIdWithNullIdReturnsNull() {
    assertNull(mongoGridFsService.findById(null));
    assertNull(mongoGridFsService.findById(""));
  }

  @Test
  void findByIdWhenFileNotFoundReturnsNull() {
    when(gridFsOperations.findOne(any(Query.class))).thenReturn(null);
    String result = mongoGridFsService.findById(new ObjectId().toString());
    assertNull(result);
  }

  @Test
  void findByIdThrowsMongoGridFSExceptionOnIOException() throws IOException {
    String gridFsId = new ObjectId().toString();
    GridFSFile mockFile = mock(GridFSFile.class);
    GridFsResource mockResource = mock(GridFsResource.class);

    when(gridFsOperations.findOne(any(Query.class))).thenReturn(mockFile);
    when(gridFsOperations.getResource(mockFile)).thenReturn(mockResource);
    when(mockResource.getInputStream()).thenThrow(new IOException("Simulated failure"));

    MongoGridFSException exception =
        assertThrows(MongoGridFSException.class, () -> mongoGridFsService.findById(gridFsId));
    assertTrue(exception.getMessage().contains("Failed to read GridFS content"));
  }

  @Test
  void saveDelegatesToOperations() {
    ByteArrayInputStream stream = new ByteArrayInputStream("test".getBytes());
    String filename = "file.txt";
    String contentType = "text/plain";
    ObjectId expectedId = new ObjectId();

    when(gridFsOperations.store(stream, filename, contentType)).thenReturn(expectedId);

    ObjectId result = mongoGridFsService.save(stream, filename, contentType);
    assertEquals(expectedId, result);
  }
}
