package cms.gov.madie.measure.config.mongock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;

@ExtendWith(MockitoExtension.class)
public class UpdateMeasureVersionChangeUnitTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private Measure measure;
  @InjectMocks private UpdateMeasureVersionChangeUnit updateMeasureVersionChangeUnit;

  private Measure testMeasure1;
  private Measure testMeasure2;
  private Measure testMeasure3;
  private Measure testMeasure4;
  private MeasureMetaData measureMetaData;

  @BeforeEach
  public void setUp() {
    testMeasure1 =
        Measure.builder()
            .id("testId1")
            .revisionNumber("002")
            .version(Version.builder().major(1).minor(2).revisionNumber(3).build())
            .build();
    testMeasure2 =
        Measure.builder()
            .id("testId2")
            .version(Version.builder().major(1).minor(2).revisionNumber(3).build())
            .build();
    testMeasure3 = Measure.builder().id("testId3").revisionNumber("002").build();
    measureMetaData = new MeasureMetaData();
    measureMetaData.setDraft(false);
    testMeasure4 =
        Measure.builder()
            .id("testId4")
            .version(Version.builder().major(0).minor(0).revisionNumber(0).build())
            .revisionNumber("000")
            .build();
    testMeasure4.setMeasureMetaData(measureMetaData);
  }

  @Test
  public void updateMeasureVersionSuccess() throws Exception {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure1));
    updateMeasureVersionChangeUnit.updateMeasureVersion(measureRepository);

    assertEquals("1.2.002", testMeasure1.getVersion().toString());
    assertNull(testMeasure1.getRevisionNumber());
    assertTrue(testMeasure1.getMeasureMetaData().isDraft());
    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(testMeasure1);
  }

  @Test
  public void updateMeasureVersionSuccessNoRevisionNumber() throws Exception {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure2));
    updateMeasureVersionChangeUnit.updateMeasureVersion(measureRepository);

    assertEquals("1.2.003", testMeasure1.getVersion().toString());
    assertNull(testMeasure2.getRevisionNumber());
    assertTrue(testMeasure2.getMeasureMetaData().isDraft());
    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(testMeasure2);
  }

  @Test
  public void updateMeasureVersionSuccessNoVersion() throws Exception {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure3));
    updateMeasureVersionChangeUnit.updateMeasureVersion(measureRepository);

    assertEquals("0.0.000", testMeasure3.getVersion().toString());
    assertNull(testMeasure3.getRevisionNumber());
    assertTrue(testMeasure3.getMeasureMetaData().isDraft());
    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(testMeasure3);
  }

  @Test
  public void updateMadieMeasureVersionSuccess() throws Exception {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure4));
    updateMeasureVersionChangeUnit.updateMeasureVersion(measureRepository);

    assertEquals("0.0.000", testMeasure4.getVersion().toString());
    assertNull(testMeasure4.getRevisionNumber());
    assertTrue(testMeasure4.getMeasureMetaData().isDraft());
    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(testMeasure4);
  }

  @Test
  public void testRollbackExecutionSuccess() throws Exception {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure1));
    updateMeasureVersionChangeUnit.rollbackExecution(measureRepository);

    assertEquals("1.2.003", testMeasure1.getRevisionNumber());
    assertTrue(testMeasure1.getMeasureMetaData().isDraft());
    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(testMeasure1);
  }

  @Test
  public void testRollbackExecutionSuccessVersionNull() throws Exception {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure3));
    updateMeasureVersionChangeUnit.rollbackExecution(measureRepository);

    assertEquals("0.0.000", testMeasure3.getRevisionNumber());
    assertTrue(testMeasure3.getMeasureMetaData().isDraft());
    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(testMeasure3);
  }

  @Test
  public void testRollbackExecutionSuccessForMadieMeasure() throws Exception {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure4));
    updateMeasureVersionChangeUnit.rollbackExecution(measureRepository);

    assertEquals("0.0.000", testMeasure4.getRevisionNumber());
    assertTrue(testMeasure4.getMeasureMetaData().isDraft());
    verify(measureRepository, new Times(1)).findAll();
    verify(measureRepository, new Times(1)).save(testMeasure4);
  }
}
