package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AddVersionDateAndCreatedBeforeVersioningChangeUnitTest {

  @Mock private MeasureRepository measureRepository;
  @InjectMocks private AddVersionDateAndCreatedBeforeVersioningChangeUnit changeUnit;
  private Measure testMeasure1;
  private Measure testMeasure2;

  @BeforeEach
  public void setUp() {
    TestCase testCase = TestCase.builder().id("testCaseId").build();
    testMeasure1 =
        Measure.builder()
            .id("testId1")
            .revisionNumber("002")
            .version(Version.builder().major(1).minor(2).revisionNumber(3).build())
            .versionId(UUID.randomUUID().toString())
            .lastModifiedAt(Instant.now())
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .testCases(List.of(testCase))
            .build();
    testMeasure2 =
        Measure.builder()
            .id("testId2")
            .version(Version.builder().major(1).minor(2).revisionNumber(3).build())
            .versionId("123")
            .lastModifiedAt(Instant.now())
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .testCases(List.of(testCase))
            .build();
  }

  @Test
  void testUpdateMeasureVersionSuccess() {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure1, testMeasure2));
    when(measureRepository.save(any(Measure.class))).thenReturn(testMeasure1);
    changeUnit.addVersionDateAndCreatedBeforeVersioningChangeUnit(measureRepository);
    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(any(Measure.class));
  }

  @Test
  void testRollbackExecutionDoesNothing() {
    changeUnit.rollbackExecution();
    verifyNoInteractions(measureRepository);
  }
}
