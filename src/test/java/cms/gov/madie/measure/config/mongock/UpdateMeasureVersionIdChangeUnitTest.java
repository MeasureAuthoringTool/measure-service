package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateMeasureVersionIdChangeUnitTest {

  @Mock private MeasureRepository measureRepository;
  @InjectMocks private UpdateMeasureVersionIdChangeUnit changeUnit;

  private Measure testMeasure1;
  private Measure testMeasure2;

  @BeforeEach
  public void setUp() {
    testMeasure1 =
        Measure.builder()
            .id("testId1")
            .revisionNumber("002")
            .version(Version.builder().major(1).minor(2).revisionNumber(3).build())
            .versionId(UUID.randomUUID().toString())
            .build();
    testMeasure2 =
        Measure.builder()
            .id("testId2")
            .version(Version.builder().major(1).minor(2).revisionNumber(3).build())
            .versionId("123")
            .build();
  }

  @Test
  void testUpdateMeasureVersionSuccess() {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure1, testMeasure2));
    when(measureRepository.save(any(Measure.class))).thenReturn(testMeasure2);
    changeUnit.updateMeasureVersionId(measureRepository);

    Assertions.assertThatNoException()
        .isThrownBy(() -> UUID.fromString(testMeasure2.getVersionId()));
    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(any(Measure.class));
  }

  @Test
  void testRollbackExecutionDoesNothing() {
    changeUnit.rollbackExecution();
    verifyNoInteractions(measureRepository);
  }
}
