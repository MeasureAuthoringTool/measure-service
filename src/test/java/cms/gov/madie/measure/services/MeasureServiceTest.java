package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.BundleOperationException;
import cms.gov.madie.measure.exceptions.CqlElmTranslationErrorException;
import cms.gov.madie.measure.exceptions.InvalidDeletionCredentialsException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import gov.cms.madie.models.measure.ElmJson;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureScoring;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseGroupPopulation;
import gov.cms.madie.models.measure.TestCasePopulationValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MeasureServiceTest {
  @Mock private MeasureRepository measureRepository;

  @Mock private FhirServicesClient fhirServicesClient;

  @Mock private ElmTranslatorClient elmTranslatorClient;

  @InjectMocks private MeasureService measureService;

  private Group group1;
  private Group group2;
  private Measure measure;

  @BeforeEach
  public void setUp() {
    // new group, not in DB, so no ID
    group1 =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1", PopulationType.INITIAL_POPULATION, "Initial Population")))
            .groupDescription("Description")
            .build();
    // Present in DB and has ID
    group2 =
        Group.builder()
            .id("xyz-p12r-12ert")
            .populations(
                List.of(
                    new Population("id-1", PopulationType.INITIAL_POPULATION, "FactorialOfFive")))
            .groupDescription("Description")
            .build();

    List<Group> groups = new ArrayList<>();
    groups.add(group2);
    measure =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .cql("test cql")
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version("0.001")
            .groups(groups)
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .build();
  }

  @Test
  public void testFindAllByActiveOmitsAndRetrievesCorrectly() {
    Measure m1 =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-459b")
            .measureName("Measure1")
            .cqlLibraryName("TestLib1")
            .createdBy("test-okta-user-id-123")
            .model("QI-Core")
            .build();
    Measure m2 =
        Measure.builder()
            .id("xyz-p13r-459a")
            .active(false)
            .measureName("Measure2")
            .cqlLibraryName("TestLib2")
            .createdBy("test-okta-user-id-123")
            .model("QI-Core")
            .active(true)
            .build();
    Page<Measure> activeMeasures = new PageImpl<>(List.of(measure, m1));
    Page<Measure> inactiveMeasures = new PageImpl<>(List.of(m2));
    PageRequest initialPage = PageRequest.of(0, 10);

    when(measureRepository.findAllByActive(eq(true), any(PageRequest.class)))
        .thenReturn(activeMeasures);
    when(measureRepository.findAllByActive(eq(false), any(PageRequest.class)))
        .thenReturn(inactiveMeasures);

    assertEquals(measureRepository.findAllByActive(true, initialPage), activeMeasures);
    assertEquals(measureRepository.findAllByActive(false, initialPage), inactiveMeasures);
    // Inactive measure id is not present in active measures
    assertFalse(activeMeasures.stream().anyMatch(item -> "xyz-p13r-459a".equals(item.getId())));
    // but is in inactive measures
    assertTrue(inactiveMeasures.stream().anyMatch(item -> "xyz-p13r-459a".equals(item.getId())));
  }

  @Test
  public void testCreateGroupWhenNoMeasureGroupsPresent() {
    // no measure group present
    measure.setGroups(null);
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    Mockito.doReturn(optional).when(measureRepository).findById(any(String.class));

    Mockito.doReturn(measure).when(measureRepository).save(any(Measure.class));

    Group persistedGroup = measureService.createOrUpdateGroup(group1, measure.getId(), "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(group1.getId(), persistedGroup.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getGroups());
    assertEquals(1, savedMeasure.getGroups().size());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    assertEquals("Cohort", capturedGroup.getScoring());
    assertEquals(
        PopulationType.INITIAL_POPULATION, capturedGroup.getPopulations().get(0).getName());
    assertEquals("Initial Population", capturedGroup.getPopulations().get(0).getDefinition());
    assertEquals("Description", capturedGroup.getGroupDescription());
  }

  @Test
  public void testCreateGroupWhenMeasureGroupsAreMultiple() {
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    Mockito.doReturn(optional).when(measureRepository).findById(any(String.class));

    Mockito.doReturn(measure).when(measureRepository).save(any(Measure.class));

    Group persistedGroup = measureService.createOrUpdateGroup(group1, measure.getId(), "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(group1.getId(), persistedGroup.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getGroups());
    assertEquals(2, savedMeasure.getGroups().size());
    Group capturedGroup = savedMeasure.getGroups().get(1);
    assertEquals("Cohort", capturedGroup.getScoring());
    assertEquals("Initial Population", capturedGroup.getPopulations().get(0).getDefinition());
    assertEquals(
        PopulationType.INITIAL_POPULATION, capturedGroup.getPopulations().get(0).getName());
    assertEquals("Description", capturedGroup.getGroupDescription());
  }

  @Test
  public void testUpdateGroup() {
    // make both group IDs same, to simulate update to the group
    group1.setId(group2.getId());

    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    Mockito.doReturn(optional).when(measureRepository).findById(any(String.class));

    Mockito.doReturn(measure).when(measureRepository).save(any(Measure.class));

    // before update
    assertEquals(
        "FactorialOfFive", measure.getGroups().get(0).getPopulations().get(0).getDefinition());

    Group persistedGroup = measureService.createOrUpdateGroup(group1, measure.getId(), "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(group1.getId(), persistedGroup.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getGroups());
    assertEquals(1, savedMeasure.getGroups().size());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    // after update
    assertEquals("Initial Population", capturedGroup.getPopulations().get(0).getDefinition());
    assertEquals("Description", capturedGroup.getGroupDescription());
  }

  @Test
  public void testInvalidDeletionCredentialsThrowsExceptionForDifferentUsers() {
    assertThrows(
        InvalidDeletionCredentialsException.class,
        () -> measureService.checkDeletionCredentials("user1", "user2"));
  }

  @Test
  public void testInvalidDeletionCredentialsDoesNotThrowExceptionWhenMatch() {
    try {
      measureService.checkDeletionCredentials("user1", "user1");
    } catch (Exception e) {
      fail("Unexpected exception was thrown");
    }
  }

  @Test
  public void testUpdateGroupChangingScoring() {
    // make both group IDs same, to simulate update to the group
    group1.setId(group2.getId());
    group2.setScoring(MeasureScoring.CONTINUOUS_VARIABLE.toString());

    // existing population referencing the group that exists in the DB
    final TestCaseGroupPopulation tcGroupPop =
        TestCaseGroupPopulation.builder()
            .groupId(group2.getId())
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .expected(true)
                        .build(),
                    TestCasePopulationValue.builder()
                        .name(PopulationType.MEASURE_POPULATION)
                        .expected(true)
                        .build()))
            .build();

    final List<TestCase> testCases =
        List.of(TestCase.builder().groupPopulations(List.of(tcGroupPop)).build());
    measure.setTestCases(testCases);

    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    Mockito.doReturn(optional).when(measureRepository).findById(any(String.class));

    Mockito.doReturn(measure).when(measureRepository).save(any(Measure.class));

    // before update
    assertEquals(
        "FactorialOfFive", measure.getGroups().get(0).getPopulations().get(0).getDefinition());

    Group persistedGroup = measureService.createOrUpdateGroup(group1, measure.getId(), "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(group1.getId(), persistedGroup.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getGroups());
    assertEquals(1, savedMeasure.getGroups().size());
    assertNotNull(savedMeasure.getTestCases());
    assertEquals(1, savedMeasure.getTestCases().size());
    assertNotNull(savedMeasure.getTestCases().get(0));
    assertNotNull(savedMeasure.getTestCases().get(0).getGroupPopulations());
    assertFalse(savedMeasure.getTestCases().get(0).getGroupPopulations().isEmpty());
    assertEquals(1, savedMeasure.getTestCases().get(0).getGroupPopulations().size());
    TestCaseGroupPopulation outputGroupPopulation =
        savedMeasure.getTestCases().get(0).getGroupPopulations().get(0);
    assertEquals("Cohort", outputGroupPopulation.getScoring());
    assertNotNull(outputGroupPopulation.getPopulationValues());
    assertEquals(1, outputGroupPopulation.getPopulationValues().size());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    // after update
    assertEquals("Initial Population", capturedGroup.getPopulations().get(0).getDefinition());
  }

  // Todo test case populations do reset on change of a group, Will be handled in a future story.

  //  @Test
  //  public void testUpdateGroupChangingPopulationsDoesNotResetExpectedValues() {
  //    // make both group IDs same, to simulate update to the group
  //    group1.setId(group2.getId());
  //    group1.setScoring(MeasureScoring.RATIO.toString());
  //    group1.setPopulation(
  //        Map.of(
  //            MeasurePopulation.INITIAL_POPULATION, "Initial Population",
  //            MeasurePopulation.NUMERATOR, "Numer",
  //            MeasurePopulation.DENOMINATOR, "Denom",
  //            MeasurePopulation.DENOMINATOR_EXCLUSION, "DenomExcl"));
  //    // keep same scoring
  //    group2.setScoring(MeasureScoring.RATIO.toString());
  //    group2.setPopulation(
  //        Map.of(
  //            MeasurePopulation.INITIAL_POPULATION, "FactorialOfFive",
  //            MeasurePopulation.NUMERATOR, "Numer",
  //            MeasurePopulation.DENOMINATOR, "Denom"));
  //
  //    // existing population referencing the group that exists in the DB
  //    final TestCaseGroupPopulation tcGroupPop =
  //        TestCaseGroupPopulation.builder()
  //            .groupId(group2.getId())
  //            .scoring(MeasureScoring.RATIO.toString())
  //            .populationValues(
  //                List.of(
  //                    TestCasePopulationValue.builder()
  //                        .name(MeasurePopulation.INITIAL_POPULATION)
  //                        .expected(true)
  //                        .build(),
  //                    TestCasePopulationValue.builder()
  //                        .name(MeasurePopulation.NUMERATOR)
  //                        .expected(false)
  //                        .build(),
  //                    TestCasePopulationValue.builder()
  //                        .name(MeasurePopulation.DENOMINATOR)
  //                        .expected(true)
  //                        .build()))
  //            .build();
  //
  //    final List<TestCase> testCases =
  //        List.of(TestCase.builder().groupPopulations(List.of(tcGroupPop)).build());
  //    measure.setTestCases(testCases);
  //
  //    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
  //    Optional<Measure> optional = Optional.of(measure);
  //    Mockito.doReturn(optional).when(repository).findById(any(String.class));
  //
  //    Mockito.doReturn(measure).when(repository).save(any(Measure.class));
  //
  //    // before update
  //    assertEquals(
  //        "FactorialOfFive",
  //        measure.getGroups().get(0).getPopulation().get(MeasurePopulation.INITIAL_POPULATION));
  //
  //    Group persistedGroup = measureService.createOrUpdateGroup(group1, measure.getId(),
  // "test.user");
  //
  //    verify(repository, times(1)).save(measureCaptor.capture());
  //    assertEquals(group1.getId(), persistedGroup.getId());
  //    Measure savedMeasure = measureCaptor.getValue();
  //    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
  //    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
  //    assertNotNull(savedMeasure.getGroups());
  //    assertEquals(1, savedMeasure.getGroups().size());
  //    assertNotNull(savedMeasure.getTestCases());
  //    assertEquals(1, savedMeasure.getTestCases().size());
  //    assertNotNull(savedMeasure.getTestCases().get(0));
  //    assertNotNull(savedMeasure.getTestCases().get(0).getGroupPopulations());
  //    assertFalse(savedMeasure.getTestCases().get(0).getGroupPopulations().isEmpty());
  //    assertEquals(1, savedMeasure.getTestCases().get(0).getGroupPopulations().size());
  //    TestCaseGroupPopulation outputGroupPopulation =
  //        savedMeasure.getTestCases().get(0).getGroupPopulations().get(0);
  //    assertEquals(MeasureScoring.RATIO.toString(), outputGroupPopulation.getScoring());
  //    assertNotNull(outputGroupPopulation.getPopulationValues());
  //    assertEquals(tcGroupPop, outputGroupPopulation);
  //    Group capturedGroup = savedMeasure.getGroups().get(0);
  //    // after update
  //    assertEquals(
  //        "Initial Population",
  //        capturedGroup.getPopulation().get(MeasurePopulation.INITIAL_POPULATION));
  //    assertEquals("Description", capturedGroup.getGroupDescription());
  //  }

  @Test
  public void testCreateOrUpdateGroupWhenMeasureDoesNotExist() {
    Optional<Measure> optional = Optional.empty();
    when(measureRepository.findById(anyString())).thenReturn(optional);
    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.createOrUpdateGroup(group1, "test", "test.user"));
  }

  @Test
  public void testResetPopulationValuesForGroupHandlesNullGroup() {
    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(
                    List.of(TestCaseGroupPopulation.builder().groupId("Group1_ID").build()))
                .build());

    List<TestCase> output = measureService.setPopulationValuesForGroup(null, testCases);
    assertEquals(testCases, output);
  }

  @Test
  public void testResetGroupPopulationValuesForGroupHandlesNullGroupId() {
    final Group group = Group.builder().id(null).scoring("Cohort").build();
    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(
                    List.of(TestCaseGroupPopulation.builder().groupId("Group1_ID").build()))
                .build());

    List<TestCase> output = measureService.setPopulationValuesForGroup(group, testCases);
    assertEquals(testCases, output);
  }

  @Test
  public void testResetGroupPopulationValuesForGroupHandlesEmptyGroupId() {
    final Group group = Group.builder().id("").scoring("Cohort").build();
    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(
                    List.of(TestCaseGroupPopulation.builder().groupId("Group1_ID").build()))
                .build());

    List<TestCase> output = measureService.setPopulationValuesForGroup(group, testCases);
    assertEquals(testCases, output);
  }

  @Test
  public void testResetGroupPopulationValuesForGroupHandlesNullGroupScoring() {
    final Group group = Group.builder().id("Group1_ID").scoring(null).build();
    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(
                    List.of(TestCaseGroupPopulation.builder().groupId("Group1_ID").build()))
                .build());

    List<TestCase> output = measureService.setPopulationValuesForGroup(group, testCases);
    assertEquals(testCases, output);
  }

  @Test
  public void testResetGroupPopulationValuesForGroupHandlesEmptyGroupScoring() {
    final Group group = Group.builder().id("Group1_ID").scoring("").build();
    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(
                    List.of(TestCaseGroupPopulation.builder().groupId("Group1_ID").build()))
                .build());

    List<TestCase> output = measureService.setPopulationValuesForGroup(group, testCases);
    assertEquals(testCases, output);
  }

  @Test
  public void testResetPopulationValuesForGroupHandlesNullTestCaseList() {
    final Group group = Group.builder().id("Group1_ID").scoring("Cohort").build();
    final List<TestCase> testCases = null;

    List<TestCase> output = measureService.setPopulationValuesForGroup(group, testCases);
    assertEquals(testCases, output);
  }

  @Test
  public void testResetPopulationValuesForGroupHandlesEmptyTestCaseList() {
    final Group group = Group.builder().id("Group1_ID").scoring("Cohort").build();
    final List<TestCase> testCases = List.of();

    List<TestCase> output = measureService.setPopulationValuesForGroup(group, testCases);
    assertEquals(testCases, output);
  }

  @Test
  public void testResetPopulationValuesForGroupHandlesNullGroupPopulationList() {
    final Group group = Group.builder().id("Group2_ID").scoring("Cohort").build();
    final List<TestCase> testCases = List.of(TestCase.builder().groupPopulations(null).build());

    List<TestCase> output = measureService.setPopulationValuesForGroup(group, testCases);
    assertEquals(testCases, output);
  }

  @Test
  public void testResetPopulationValuesForGroupHandlesNonMatchingID() {
    final Group group = Group.builder().id("Group2_ID").scoring("Cohort").build();
    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(
                    List.of(
                        TestCaseGroupPopulation.builder()
                            .groupId("Group1_ID")
                            .scoring(MeasureScoring.COHORT.toString())
                            .populationValues(
                                List.of(
                                    TestCasePopulationValue.builder()
                                        .name(PopulationType.INITIAL_POPULATION)
                                        .expected(true)
                                        .build()))
                            .build()))
                .build());

    List<TestCase> output = measureService.setPopulationValuesForGroup(group, testCases);
    assertEquals(testCases, output);
  }

  @Test
  public void testResetPopulationValuesForGroupHandlesMatchingID() {
    final Group group = Group.builder().id("Group2_ID").scoring("Cohort").build();
    TestCaseGroupPopulation testCaseGroupPopulation1 =
        TestCaseGroupPopulation.builder()
            .groupId("Group1_ID")
            .scoring(MeasureScoring.COHORT.toString())
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .expected(true)
                        .build()))
            .build();

    final TestCaseGroupPopulation testCaseGroupPopulation2 =
        TestCaseGroupPopulation.builder()
            .groupId("Group2_ID")
            .scoring(MeasureScoring.CONTINUOUS_VARIABLE.toString())
            .populationValues(
                List.of(
                    TestCasePopulationValue.builder()
                        .name(PopulationType.INITIAL_POPULATION)
                        .expected(true)
                        .build(),
                    TestCasePopulationValue.builder()
                        .name(PopulationType.MEASURE_POPULATION)
                        .expected(true)
                        .build()))
            .build();

    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(List.of(testCaseGroupPopulation1, testCaseGroupPopulation2))
                .build());

    List<TestCase> output = measureService.setPopulationValuesForGroup(group, testCases);
    assertNotNull(output);
    assertNotEquals(testCases, output);
    assertEquals(1, output.size());
    assertNotNull(output.get(0));
    assertNotNull(output.get(0).getGroupPopulations());
    assertEquals(2, output.get(0).getGroupPopulations().size());
    assertEquals(testCaseGroupPopulation1, output.get(0).getGroupPopulations().get(0));
    assertNotNull(output.get(0).getGroupPopulations().get(1).getPopulationValues());
    assertEquals("Cohort", output.get(0).getGroupPopulations().get(1).getScoring());
    assertEquals(0, output.get(0).getGroupPopulations().get(1).getPopulationValues().size());
  }

  @Test
  void testBundleMeasureReturnsNullForNullMeasure() {
    String output = measureService.bundleMeasure(null, "Bearer TOKEN");
    assertThat(output, is(nullValue()));
  }

  @Test
  void testBundleMeasureThrowsOperationException() {
    final Measure measure = Measure.builder().createdBy("test.user").cql("CQL").build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{}").xml("<></>").build());
    when(fhirServicesClient.getMeasureBundle(any(Measure.class), anyString()))
        .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
    assertThrows(
        BundleOperationException.class,
        () -> measureService.bundleMeasure(measure, "Bearer TOKEN"));
  }

  @Test
  void testBundleMeasureThrowsCqlElmTranslatorExceptionWithErrors() {
    final Measure measure = Measure.builder().createdBy("test.user").cql("CQL").build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{}").xml("<></>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(true);
    assertThrows(
        CqlElmTranslationErrorException.class,
        () -> measureService.bundleMeasure(measure, "Bearer TOKEN"));
  }

  @Test
  void testBundleMeasureReturnsBundleString() {
    final String json = "{\"message\": \"GOOD JSON\"}";
    final Measure measure = Measure.builder().createdBy("test.user").cql("CQL").build();
    when(fhirServicesClient.getMeasureBundle(any(Measure.class), anyString())).thenReturn(json);
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{}").xml("<></>").build());
    String output = measureService.bundleMeasure(measure, "Bearer TOKEN");
    assertThat(output, is(equalTo(json)));
  }

  @Test
  public void testVerifyAuthorizationThrowsExceptionForDifferentUsers() {
    assertThrows(
        UnauthorizedException.class, () -> measureService.verifyAuthorization("user1", measure));
  }

  @Test
  public void testCheckDuplicateCqlLibraryNameDoesNotThrowException() {
    Optional<Measure> measureOpt = Optional.empty();
    when(measureRepository.findByCqlLibraryName(anyString())).thenReturn(measureOpt);
    measureService.checkDuplicateCqlLibraryName("testCQLLibraryName");
    verify(measureRepository, times(1)).findByCqlLibraryName(eq("testCQLLibraryName"));
  }

  @Test
  public void testCheckDuplicateCqlLibraryNameThrowsExceptionForExistingName() {
    final Measure measure = Measure.builder().cqlLibraryName("testCQLLibraryName").build();
    Optional<Measure> measureOpt = Optional.of(measure);
    when(measureRepository.findByCqlLibraryName(anyString())).thenReturn(measureOpt);
    assertThrows(
        DuplicateKeyException.class,
        () -> measureService.checkDuplicateCqlLibraryName("testCQLLibraryName"));
  }
}
