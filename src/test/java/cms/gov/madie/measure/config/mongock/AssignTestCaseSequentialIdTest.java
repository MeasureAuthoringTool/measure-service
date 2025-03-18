package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.TestCaseSequenceRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignTestCaseSequentialIdTest {

  @Mock MeasureRepository measureRepository;
  @Mock TestCaseSequenceRepository testCaseSequenceRepository;

  @InjectMocks AssignTestCaseSequentialId assignTestCaseSequentialId;

  Measure measure1;
  Measure measure2;
  TestCase tc1;
  TestCase tc2;
  TestCase tc3;
  TestCase tc4;
  TestCase tc5;
  TestCase tc6;

  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor1;
  @Captor private ArgumentCaptor<TestCaseSequence> testCaseSequenceArgumentCaptor;

  @BeforeEach
  public void setup() {
    Instant now = Instant.now();
    tc5 = TestCase.builder().id("TC5").name("TC%").createdAt(now.plusSeconds(1)).build();
    tc1 = TestCase.builder().id("TC1").name("TC1").createdAt(now.plusSeconds(2)).build();
    tc4 = TestCase.builder().id("TC4").name("TC4").createdAt(now.plusSeconds(3)).build();
    tc3 = TestCase.builder().id("TC3").name("TC3").createdAt(now.plusSeconds(4)).build();
    tc6 = TestCase.builder().id("TC6").name("TC6").createdAt(now.plusSeconds(5)).build();
    tc2 = TestCase.builder().id("TC2").name("TC2").createdAt(now.plusSeconds(6)).build();

    measure1 =
        Measure.builder()
            .id("Measure1")
            .measureName("Measure1")
            .measureMetaData(new MeasureMetaData().toBuilder().draft(true).build())
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of(tc1, tc2, tc3, tc4, tc5, tc6))
            .build();

    measure2 =
        Measure.builder()
            .id("Measure2")
            .measureName("Measure2")
            .measureMetaData(new MeasureMetaData().toBuilder().draft(false).build())
            .model(ModelType.QDM_5_6.getValue())
            .testCases(List.of(tc1, tc2, tc3, tc4, tc5, tc6))
            .build();
  }

  @Test
  void testChangeUnitExecutionEmptyRepository() {
    when(measureRepository.findAll()).thenReturn(List.of());
    assignTestCaseSequentialId.updateTestCaseWithSequentialCaseNumber(
        measureRepository, testCaseSequenceRepository);
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  void assignTestCaseSequentialIdForDraftMeasures() {
    when(measureRepository.findAll()).thenReturn(List.of(measure1, measure2));

    assignTestCaseSequentialId.updateTestCaseWithSequentialCaseNumber(
        measureRepository, testCaseSequenceRepository);
    // Versioned measure are not modified
    verify(measureRepository, times(1)).save(measureArgumentCaptor1.capture());
    Measure measure = measureArgumentCaptor1.getValue();
    assertThat(measure.getTestCases(), is(notNullValue()));
    assertThat(measure.getTestCases().size(), is(equalTo(6)));
    List<TestCase> sortedTestCases =
        measure.getTestCases().stream()
            .sorted(Comparator.comparing(TestCase::getCreatedAt))
            .toList();
    // Testcases are inserted and updated based on createdAt field
    assertEquals("TC5", sortedTestCases.get(0).getId());
    assertEquals(1, sortedTestCases.get(0).getCaseNumber());
    assertEquals("TC1", sortedTestCases.get(1).getId());
    assertEquals(2, sortedTestCases.get(1).getCaseNumber());
    assertEquals("TC4", sortedTestCases.get(2).getId());
    assertEquals(3, sortedTestCases.get(2).getCaseNumber());
    assertEquals("TC3", sortedTestCases.get(3).getId());
    assertEquals(4, sortedTestCases.get(3).getCaseNumber());
    assertEquals("TC6", sortedTestCases.get(4).getId());
    assertEquals(5, sortedTestCases.get(4).getCaseNumber());
    assertEquals("TC2", sortedTestCases.get(5).getId());
    assertEquals(6, sortedTestCases.get(5).getCaseNumber());

    verify(testCaseSequenceRepository, times(1)).save(testCaseSequenceArgumentCaptor.capture());
    TestCaseSequence testCaseSequence = testCaseSequenceArgumentCaptor.getValue();
    assertEquals(6, testCaseSequence.getSequence());
  }
}
