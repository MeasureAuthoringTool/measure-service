package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import com.mongodb.bulk.BulkWriteResult;
import gov.cms.madie.models.measure.Measure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AddIncludedLibrariesPropChangeUnitTest {
  @Mock MeasureRepository repository;
  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks AddIncludedLibrariesPropChangeUnit changeUnit;

  @Test
  void testAddIncludedLibrariesPropIfNoLibraryExists() {
    when(repository.findAll()).thenReturn(List.of());
    changeUnit.addIncludedLibrariesProp(repository);
    verify(repository, new Times(0)).saveAll(any(List.class));
  }

  @Test
  void testAddIncludedLibrariesProp() {
    String cql1 =
        "library CWBCAFHIR version '0.0.000'\n"
            + "using QICore version '4.1.1'\n"
            + "include FHIRHelpers version '4.3.000' called FHIRHelpers\n"
            + "include SupplementalDataElements version '3.4.000' called SDE";
    String cql2 =
        "library CaseWhenThenddd version '0.3.000'\n"
            + "using QDM version '5.6'\n"
            + "include \"CancerLinQ\" version '1.5.000' called CancerLinQQ\n";
    Measure measure1 = Measure.builder().cql(cql1).build();
    Measure measure2 = Measure.builder().cql(cql2).build();

    when(repository.findAll()).thenReturn(List.of(measure1, measure2));
    when(repository.saveAll(any(List.class))).thenReturn(List.of(measure1, measure2));
    changeUnit.addIncludedLibrariesProp(repository);

    verify(repository, new Times(1)).saveAll(any(List.class));
  }

  @Test
  void testRollbackExecution() {
    BulkWriteResult result = mock(BulkWriteResult.class);
    BulkOperations bulkOperations = mock(BulkOperations.class);
    when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Measure.class)))
        .thenReturn(bulkOperations);
    when(bulkOperations.execute()).thenReturn(result);
    changeUnit.rollbackExecution(mongoTemplate);
    verify(bulkOperations, new Times(1)).updateMulti(any(Query.class), any(Update.class));
    verify(bulkOperations, new Times(1)).execute();
  }
}
