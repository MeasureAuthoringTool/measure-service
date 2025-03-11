package cms.gov.madie.measure.config.mongock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.TestCase;

@ExtendWith(MockitoExtension.class)
public class AddPatientIdChangeUnitTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private Measure measure;
  @InjectMocks private AddPatientIdChangeUnit addPatientIdChangeUnit;

  private Measure testMeasure;
  private TestCase testTestCase;

  @BeforeEach
  public void setUp() {
    testTestCase = TestCase.builder().id("testCaseId").title("testCaseTitle").build();

    testMeasure = Measure.builder().id("testMeasureId").testCases(List.of(testTestCase)).build();
  }

  @Test
  public void addPatientIdChangeUnitSuccess() {
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure));
    addPatientIdChangeUnit.addTestCasePatientId(measureRepository);

    UUID patientId = testMeasure.getTestCases().get(0).getPatientId();
    assertNotNull(patientId);
    String regex = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
    assertTrue(patientId.toString().matches(regex));
    assertEquals(UUID.fromString(patientId.toString()).toString(), patientId.toString());
  }

  @Test
  public void testAddPatientIdChangeUnitNoMeasures() {
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    when(measureRepository.findAll()).thenReturn(new ArrayList<Measure>());
    addPatientIdChangeUnit.addTestCasePatientId(measureRepository);

    verify(measureRepository, times(0)).save(measureCaptor.capture());
  }

  @Test
  public void testAddPatientIdChangeUnitTestCasePatientIdNotNull() {
    UUID patientId = UUID.fromString("b8e59126-fa80-4264-a71c-b0b24f4939ef");
    testTestCase =
        TestCase.builder().id("testCaseId").title("testCaseTitle").patientId(patientId).build();
    testMeasure = Measure.builder().id("testMeasureId").testCases(List.of(testTestCase)).build();

    when(measureRepository.findAll()).thenReturn(List.of(testMeasure));
    addPatientIdChangeUnit.addTestCasePatientId(measureRepository);

    assertEquals(testMeasure.getTestCases().get(0).getPatientId(), patientId);
  }

  @Test
  public void testAddPatientIdChangeUnitNoTestCases() {
    testMeasure.setTestCases(new ArrayList<TestCase>());
    when(measureRepository.findAll()).thenReturn(List.of(testMeasure));
    addPatientIdChangeUnit.addTestCasePatientId(measureRepository);

    assertTrue(CollectionUtils.isEmpty(testMeasure.getTestCases()));
  }

  @Test
  public void testRollbackExecutionHasMeasures() {
    ReflectionTestUtils.setField(addPatientIdChangeUnit, "tempMeasures", List.of(testMeasure));

    addPatientIdChangeUnit.rollbackExecution(measureRepository);

    UUID patientId = testMeasure.getTestCases().get(0).getPatientId();
    assertNull(patientId);
  }

  @Test
  public void testRollbackExecutionNoMeasures() {
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    addPatientIdChangeUnit.rollbackExecution(measureRepository);

    verify(measureRepository, times(0)).save(measureCaptor.capture());
  }
}
