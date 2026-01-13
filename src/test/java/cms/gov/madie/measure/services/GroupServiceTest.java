package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.factories.ModelValidatorFactory;
import gov.cms.madie.models.measure.*;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.core.JsonProcessingException;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.utils.MeasureUtil;
import cms.gov.madie.measure.utils.ResourceUtil;
import cms.gov.madie.measure.validations.CqlDefinitionReturnTypeService;
import cms.gov.madie.measure.validations.CqlObservationFunctionService;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;

@ExtendWith(MockitoExtension.class)
public class GroupServiceTest implements ResourceUtil {
  @Mock private MeasureRepository measureRepository;

  @Mock private MeasureUtil measureUtil;

  @Mock private MeasureService measureService;

  @Mock private CqlDefinitionReturnTypeService cqlDefinitionReturnTypeService;
  @Mock private CqlObservationFunctionService cqlObservationFunctionService;

  @Mock private ModelValidatorFactory modelValidatorFactory;

  @Mock private QiCoreModelValidator qicoreModelValidator;

  @Mock private TestCaseLockService testCaseLockService;
  @Mock private AppConfigService appConfigService;

  @InjectMocks private GroupService groupService;

  private Group group1;
  private Group group2;
  private Group ratioGroup;
  private Measure measure;
  private MeasureListDTO measureList;
  private Stratification strata1;
  private Stratification stratification;
  private TestCasePopulationValue testCasePopulationValue1;
  private TestCasePopulationValue testCasePopulationValue2;
  private TestCasePopulationValue testCasePopulationValue3;
  private TestCasePopulationValue testCasePopulationValue4;

  @BeforeEach
  public void setUp() {
    strata1 = new Stratification();
    strata1.setId("strat-1");
    strata1.setCqlDefinition("Initial Population");
    strata1.setAssociation(PopulationType.INITIAL_POPULATION);
    strata1.setAssociations(List.of(PopulationType.INITIAL_POPULATION));
    Stratification strata2 = new Stratification();
    strata2.setId("strat-2");
    strata2.setCqlDefinition("Denominator");
    strata2.setAssociation(PopulationType.DENOMINATOR);

    Stratification emptyStrat = new Stratification();
    // new group, not in DB, so no ID
    group1 =
        Group.builder()
            .id("xyz-p11r-11ert")
            .displayId("Group_1")
            .scoring("Cohort")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();
    // Present in DB and has ID
    group2 =
        Group.builder()
            .id("xyz-p12r-12ert")
            .displayId("Group_2")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .scoring("Continuous Variable")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "FactorialOfFive",
                        null,
                        null,
                        "IntialPopulation_2"),
                    new Population(
                        "id-2",
                        PopulationType.MEASURE_POPULATION,
                        "Measure Population",
                        null,
                        null,
                        "MeasurePopulation_2")))
            .measureObservations(
                List.of(
                    new MeasureObservation(
                        "id-1",
                        "fun",
                        "a description of fun",
                        "id-2",
                        AggregateMethodType.MAXIMUM.getValue(),
                        "MeasureObservation_2")))
            .stratifications(List.of(strata1))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();
    // Ratio group
    ratioGroup =
        Group.builder()
            .id("ratioGroup_id")
            .scoring("Ratio")
            .populationBasis("Encounter")
            .measureGroupTypes(Arrays.asList(MeasureGroupTypes.OUTCOME))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1_1"),
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1_2"),
                    new Population(
                        "id-3",
                        PopulationType.DENOMINATOR,
                        "Denominator",
                        null,
                        null,
                        "Denominator_1"),
                    new Population(
                        "id-4", PopulationType.NUMERATOR, "Numerator", null, null, "Numerator_1"),
                    new Population(
                        "id-4",
                        PopulationType.NUMERATOR_EXCLUSION,
                        "",
                        null,
                        null,
                        "NumeratorExclusion_1")))
            .measureObservations(
                new ArrayList<>(
                    List.of(
                        new MeasureObservation(
                            "mo-id-1",
                            "fun",
                            "a description of fun",
                            "id-2",
                            AggregateMethodType.MAXIMUM.getValue(),
                            "MeasureObservation_1_1"),
                        new MeasureObservation(
                            "mo-id-2",
                            "fun",
                            "a description of fun",
                            "id-4",
                            AggregateMethodType.MAXIMUM.getValue(),
                            "MeasureObservation_1_2"))))
            .stratifications(List.of(strata1, strata2))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    List<Group> groups = new ArrayList<>();
    groups.add(group2);
    String elmJson = getData("/test_elm.json");
    measure =
        Measure.builder()
            .model(ModelType.QI_CORE.getValue())
            .active(true)
            .id("xyz-p13r-13ert")
            .cql("test cql")
            .elmJson(elmJson)
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .groups(groups)
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();

    measureList =
        MeasureListDTO.builder()
            .model(ModelType.QI_CORE.getValue())
            .active(true)
            .id("xyz-p13r-13ert")
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();

    stratification = new Stratification();
    stratification.setId("stratId");
    stratification.setCqlDefinition("truebool1");
    testCasePopulationValue1 =
        TestCasePopulationValue.builder()
            .id("testCasePopulationValue1")
            .name(PopulationType.INITIAL_POPULATION)
            .expected(Boolean.FALSE)
            .actual(Boolean.FALSE)
            .build();
    testCasePopulationValue2 =
        TestCasePopulationValue.builder()
            .id("testCasePopulationValue2")
            .name(PopulationType.DENOMINATOR)
            .expected(Boolean.FALSE)
            .actual(Boolean.FALSE)
            .build();
    testCasePopulationValue3 =
        TestCasePopulationValue.builder()
            .id("testCasePopulationValue3")
            .name(PopulationType.NUMERATOR)
            .expected(Boolean.FALSE)
            .actual(Boolean.FALSE)
            .build();
    testCasePopulationValue4 =
        TestCasePopulationValue.builder()
            .id("testCasePopulationValue4")
            .name(PopulationType.DENOMINATOR_EXCLUSION)
            .expected(Boolean.FALSE)
            .actual(Boolean.FALSE)
            .build();
  }

  @Test
  public void testFindAllByActiveOmitsAndRetrievesCorrectly() {
    MeasureListDTO m1 =
        MeasureListDTO.builder()
            .active(true)
            .id("xyz-p13r-459b")
            .measureName("Measure1")
            .model("QI-Core")
            .build();
    MeasureListDTO m2 =
        MeasureListDTO.builder()
            .id("xyz-p13r-459a")
            .active(false)
            .measureName("Measure2")
            .model("QI-Core")
            .active(true)
            .build();
    Page<MeasureListDTO> activeMeasures = new PageImpl<>(List.of(measureList, m1));
    Page<MeasureListDTO> inactiveMeasures = new PageImpl<>(List.of(m2));
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
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(measure).when(measureRepository).save(any(Measure.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    Group persistedGroup = groupService.createOrUpdateGroup(group1, measure.getId(), "test.user");

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
    assertEquals("test-scoring-unit", capturedGroup.getScoringUnit());
  }

  @Test
  public void testCreateGroupWhenMeasureGroupsAreMultiple() {
    measure.setGroups(List.of(group1, group2));
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(measure).when(measureRepository).save(any(Measure.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    Group persistedGroup = groupService.createOrUpdateGroup(group1, measure.getId(), "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(group1.getId(), persistedGroup.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getGroups());
    assertEquals(2, savedMeasure.getGroups().size());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    assertEquals("Cohort", capturedGroup.getScoring());
    assertEquals("Initial Population", capturedGroup.getPopulations().get(0).getDefinition());
    assertEquals(
        PopulationType.INITIAL_POPULATION, capturedGroup.getPopulations().get(0).getName());
    assertEquals("Description", capturedGroup.getGroupDescription());
    assertEquals("test-scoring-unit", capturedGroup.getScoringUnit());
  }

  @Test
  public void testCreateGroupWithObservationWhenMeasureGroupsAreMultiple() {
    measure.setGroups(List.of(ratioGroup, group2));
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(measure).when(measureRepository).save(any(Measure.class));

    doReturn(qicoreModelValidator)
        .when(modelValidatorFactory)
        .getModelValidator(eq(ModelType.QI_CORE));

    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    Group persistedGroup =
        groupService.createOrUpdateGroup(ratioGroup, measure.getId(), "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(ratioGroup.getId(), persistedGroup.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getGroups());
    assertEquals(2, savedMeasure.getGroups().size());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    assertEquals("Ratio", capturedGroup.getScoring());
    assertEquals("Initial Population", capturedGroup.getPopulations().get(0).getDefinition());
    assertEquals(
        PopulationType.INITIAL_POPULATION, capturedGroup.getPopulations().get(0).getName());
    assertEquals("Description", capturedGroup.getGroupDescription());
    assertEquals("test-scoring-unit", capturedGroup.getScoringUnit());
    assertNotNull(capturedGroup.getMeasureObservations());
    assertEquals(2, capturedGroup.getMeasureObservations().size());
  }

  @Test
  public void testUpdateGroup() {
    // make both group IDs same, to simulate update to the group
    group1.setId(group2.getId());

    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(measure).when(measureRepository).save(any(Measure.class));

    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    // before update
    assertEquals(
        "FactorialOfFive", measure.getGroups().get(0).getPopulations().get(0).getDefinition());

    Group persistedGroup = groupService.createOrUpdateGroup(group1, measure.getId(), "test.user");

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
    assertEquals("test-scoring-unit", capturedGroup.getScoringUnit());
  }

  @Test
  void testDeleteGroup() {
    Group group =
        Group.builder()
            .id("testgroupid")
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1")))
            .build();

    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .groups(List.of(group))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .measureSet(MeasureSet.builder().owner("test.user").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    doReturn(existingMeasure).when(measureRepository).save(any(Measure.class));

    Measure output = groupService.deleteMeasureGroup("measure-id", "testgroupid", "test.user");

    assertEquals(0, output.getGroups().size());
  }

  @Test
  void testDeleteGroupResetDisplayId() {
    Group group1 =
        Group.builder()
            .id("testgroupid1")
            .displayId("Group_1")
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-group1_1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1")))
            .build();
    Group group2 =
        Group.builder()
            .id("testgroupid2")
            .displayId("Group_2")
            .scoring("Proportion")
            .populations(
                List.of(
                    new Population(
                        "id-group2-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1"),
                    new Population(
                        "id-group2-2",
                        PopulationType.DENOMINATOR,
                        "Denominator",
                        null,
                        null,
                        "Denominator_1"),
                    new Population(
                        "id-group2-3",
                        PopulationType.NUMERATOR,
                        "Numerator",
                        null,
                        null,
                        "Numerator_1")))
            .build();

    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .groups(List.of(group1, group2))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .measureSet(MeasureSet.builder().owner("test.user").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    doReturn(existingMeasure).when(measureRepository).save(any(Measure.class));

    Measure output = groupService.deleteMeasureGroup("measure-id", "testgroupid1", "test.user");

    assertEquals(1, output.getGroups().size());
    assertEquals("Group_1", output.getGroups().get(0).getDisplayId());
    assertEquals(3, output.getGroups().get(0).getPopulations().size());
    assertEquals(
        "InitialPopulation_1", output.getGroups().get(0).getPopulations().get(0).getDisplayId());
    assertEquals("Denominator_1", output.getGroups().get(0).getPopulations().get(1).getDisplayId());
    assertEquals("Numerator_1", output.getGroups().get(0).getPopulations().get(2).getDisplayId());
  }

  @Test
  void testDeleteGroupWhenTestCaseHasNoGroups() {
    Group group =
        Group.builder()
            .id("testgroupid")
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1")))
            .build();
    List<TestCase> testCases = List.of(TestCase.builder().groupPopulations(null).build());

    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .groups(List.of(group))
            .testCases(testCases)
            .measureSet(MeasureSet.builder().owner("test.user").build())
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    doReturn(existingMeasure).when(measureRepository).save(any(Measure.class));
    // before deletion
    assertEquals(1, existingMeasure.getGroups().size());
    groupService.deleteMeasureGroup("measure-id", "testgroupid", "test.user");
    // after deletion
    assertEquals(0, existingMeasure.getGroups().size());
  }

  @Test
  void testDeleteMeasureGroupReturnsExceptionForNullMeasureId() {
    assertThrows(
        InvalidIdException.class,
        () -> groupService.deleteMeasureGroup("", "grouptestid", "OtherUser"));
  }

  @Test
  void testDeleteMeasureGroupReturnsExceptionThrowsAccessException() {
    String groupId = "testgroupid";
    final Measure measure =
        Measure.builder()
            .id("measure-id")
            .createdBy("OtherUser")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .measureSet(MeasureSet.builder().owner("OtherUser").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    doThrow(new UnauthorizedException("Measure", measure.getId(), "user2"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));
    assertThrows(
        UnauthorizedException.class,
        () -> groupService.deleteMeasureGroup("measure-id", groupId, "user2"));
  }

  @Test
  void testDeleteMeasureGroupReturnsInvalidDraftStatusException() {
    String groupId = "testgroupid";
    final Measure measure =
        Measure.builder()
            .id("measure-id")
            .createdBy("OtherUser")
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .measureSet(MeasureSet.builder().owner("OtherUser").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    assertThrows(
        InvalidDraftStatusException.class,
        () -> groupService.deleteMeasureGroup("measure-id", groupId, "user2"));
  }

  @Test
  void testDeleteMeasureGroupReturnsExceptionForResourceNotFound() {
    assertThrows(
        ResourceNotFoundException.class,
        () -> groupService.deleteMeasureGroup("testid", "testgroupid", "user2"));
  }

  @Test
  void testDeleteMeasureGroupReturnsExceptionForNullId() {
    final Measure measure =
        Measure.builder()
            .id("measure-id")
            .createdBy("OtherUser")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .measureSet(MeasureSet.builder().owner("OtherUser").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);

    assertThrows(
        InvalidIdException.class,
        () -> groupService.deleteMeasureGroup("measure-id", "", "OtherUser"));
  }

  @Test
  void testDeleteMeasureGroupReturnsExceptionForGroupNotFoundInMeasure() {
    Group group =
        Group.builder()
            .id("testgroupid")
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1")))
            .build();

    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .groups(List.of(group))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .measureSet(MeasureSet.builder().owner("test.user").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        ResourceNotFoundException.class,
        () -> groupService.deleteMeasureGroup("measure-id", "grouptestid1", "test.user"));
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
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(measure).when(measureRepository).save(any(Measure.class));

    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    // before update
    assertEquals(
        "FactorialOfFive", measure.getGroups().get(0).getPopulations().get(0).getDefinition());

    Group persistedGroup = groupService.createOrUpdateGroup(group1, measure.getId(), "test.user");

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
    assertTrue(savedMeasure.getTestCases().get(0).getGroupPopulations().isEmpty());
    assertEquals(0, savedMeasure.getTestCases().get(0).getGroupPopulations().size());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    // after update
    assertEquals("Initial Population", capturedGroup.getPopulations().get(0).getDefinition());
  }

  @Test
  public void testCreateOrUpdateGroupWhenMeasureDoesNotExist() {
    Optional<Measure> optional = Optional.empty();
    when(measureRepository.findById(anyString())).thenReturn(optional);
    assertThrows(
        ResourceNotFoundException.class,
        () -> groupService.createOrUpdateGroup(group1, "test", "test.user"));
  }

  @Test
  public void testCreateOrUpdateGroupWhenMeasureHasLockedTestCases() {
    Optional<Measure> optional = Optional.of(measure);
    when(measureRepository.findById(anyString())).thenReturn(optional);
    when(appConfigService.isFlagEnabled(any())).thenReturn(true);
    when(testCaseLockService.isAnyTestCaseLockedByOthers(anyString(), anyString()))
        .thenReturn(true);
    assertThrows(
        LockNotObtainedException.class,
        () -> groupService.createOrUpdateGroup(group1, "test", "test.user"));
  }

  @Test
  public void testResetPopulationValuesForGroupHandlesNullGroup() {
    TestCaseGroupPopulation testPopulation =
        TestCaseGroupPopulation.builder().groupId("Group1_ID").build();
    final List<TestCase> testCases =
        List.of(TestCase.builder().groupPopulations(List.of(testPopulation)).build());

    groupService.updateGroupForTestCases(null, testCases, ModelType.QI_CORE.getValue());
    // unchanged test case populations
    assertEquals(testCases.get(0).getGroupPopulations(), List.of(testPopulation));
  }

  @Test
  public void testUpdateTestCaseGroupGroupScoringChanged() {
    final Group group =
        Group.builder().id("Group1_ID").scoring("Cohort").populationBasis("Encounter").build();
    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(
                    List.of(
                        TestCaseGroupPopulation.builder()
                            .groupId("Group1_ID")
                            .scoring("Proportion")
                            .populationBasis("Encounter")
                            .build()))
                .build());
    // before updates
    assertEquals(1, testCases.get(0).getGroupPopulations().size());
    groupService.updateGroupForTestCases(group, testCases, ModelType.QI_CORE.getValue());
    // group should be removed from test case as measure group scoring was changed
    assertEquals(0, testCases.get(0).getGroupPopulations().size());
  }

  @Test
  public void testUpdateTestCaseGroupGroupScoringNotChanged() {
    final Group group =
        Group.builder()
            .id("Group1_ID")
            .scoring("Proportion")
            .populationBasis("Encounter")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1")))
            .build();
    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(
                    List.of(
                        TestCaseGroupPopulation.builder()
                            .groupId("Group1_ID")
                            .scoring("Proportion")
                            .populationBasis("Encounter")
                            .populationValues(
                                List.of(
                                    TestCasePopulationValue.builder()
                                        .name(PopulationType.INITIAL_POPULATION)
                                        .expected(true)
                                        .build()))
                            .build()))
                .build());
    // before updates
    assertEquals(1, testCases.get(0).getGroupPopulations().size());
    groupService.updateGroupForTestCases(group, testCases, ModelType.QI_CORE.getValue());
    // group should not be removed from test case as measure group scoring was not
    // changed
    assertEquals(1, testCases.get(0).getGroupPopulations().size());
  }

  @Test
  public void testUpdateTestCaseGroupGroupPopulationBasisChanged() {
    final Group group =
        Group.builder().id("Group1_ID").scoring("Cohort").populationBasis("Encounter").build();
    final List<TestCase> testCases =
        List.of(
            TestCase.builder()
                .groupPopulations(
                    List.of(
                        TestCaseGroupPopulation.builder()
                            .groupId("Group1_ID")
                            .scoring("Cohort")
                            .populationBasis("boolean")
                            .build()))
                .build());
    // before updates
    assertEquals(1, testCases.get(0).getGroupPopulations().size());
    groupService.updateGroupForTestCases(group, testCases, ModelType.QI_CORE.getValue());
    // group should be removed from test case as populationBasis for measure group
    // was changed
    assertEquals(0, testCases.get(0).getGroupPopulations().size());
  }

  @Test
  public void testResetPopulationValuesForGroupHandlesEmptyTestCaseList() {
    final Group group = Group.builder().id("Group1_ID").scoring("Cohort").build();
    final List<TestCase> testCases = List.of();
    // before updates
    assertEquals(0, testCases.size());
    groupService.updateGroupForTestCases(group, testCases, ModelType.QI_CORE.getValue());
    // after updates, no change to the test case list
    assertEquals(0, testCases.size());
  }

  @Test
  public void testResetPopulationValuesForGroupHandlesNullGroupPopulationList() {
    final Group group = Group.builder().id("Group2_ID").scoring("Cohort").build();
    final List<TestCase> testCases = List.of(TestCase.builder().groupPopulations(null).build());
    // before updates
    assertNull(testCases.get(0).getGroupPopulations());
    groupService.updateGroupForTestCases(group, testCases, ModelType.QI_CORE.getValue());
    // after updates, no population added to the group
    assertNull(testCases.get(0).getGroupPopulations());
  }

  @Test
  public void testResetPopulationValuesForNewGroup() {
    // group with id that does not exist in test case
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
    // before updates
    assertEquals(1, testCases.get(0).getGroupPopulations().size());
    groupService.updateGroupForTestCases(group, testCases, ModelType.QI_CORE.getValue());
    // after update call, do nothing for new group
    assertEquals(1, testCases.get(0).getGroupPopulations().size());
  }

  @Test
  public void testUpdateGroupChangingScoringUnit() {
    // make both group IDs same, to simulate update to the group
    group1.setId(group2.getId());
    group1.setScoringUnit("new scoring unit");

    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(measure).when(measureRepository).save(any(Measure.class));

    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    // before update
    assertEquals(
        "FactorialOfFive", measure.getGroups().get(0).getPopulations().get(0).getDefinition());

    Group persistedGroup = groupService.createOrUpdateGroup(group1, measure.getId(), "test.user");

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
    assertNotEquals("test-scoring-unit", capturedGroup.getScoringUnit());
    assertEquals("new scoring unit", capturedGroup.getScoringUnit());
  }

  @Test
  public void testUpdateGroupWhenElmJsonIsInvalid() throws JsonProcessingException {
    measure.setElmJson("UnpardonableElmJson");
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doThrow(new JsonProcessingException("invalid elm json") {})
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());

    assertThrows(
        IllegalArgumentException.class,
        () -> groupService.createOrUpdateGroup(group2, measure.getId(), "test.user"));
  }

  @Test
  public void testUpdateGroupWithValidStratification() {
    group2.setDisplayId("Group_1");
    group2.setPopulations(null);
    Optional<Measure> optional = Optional.of(measure);
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    doReturn(optional).when(measureRepository).findById(any(String.class));
    doReturn(measure).when(measureRepository).save(any(Measure.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));
    doReturn(qicoreModelValidator)
        .when(modelValidatorFactory)
        .getModelValidator(eq(ModelType.QI_CORE));

    Group group = groupService.createOrUpdateGroup(group2, measure.getId(), "test.user");
    assertEquals(group.getStratifications().size(), group2.getStratifications().size());
    verify(measureRepository, times(1)).save(measureCaptor.capture());
  }

  @Test
  public void testUpdateGroupWhenPopulationFunctionReturnTypeMatchingWithPopulationBasis() {
    group2.setDisplayId("Group_1");
    group2.setPopulations(null);
    Optional<Measure> optional = Optional.of(measure);
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    doReturn(optional).when(measureRepository).findById(any(String.class));
    doReturn(measure).when(measureRepository).save(any(Measure.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));
    doReturn(qicoreModelValidator)
        .when(modelValidatorFactory)
        .getModelValidator(eq(ModelType.QI_CORE));

    Group group = groupService.createOrUpdateGroup(group2, measure.getId(), "test.user");
    assertEquals(group.getMeasureObservations().size(), group2.getMeasureObservations().size());
    verify(measureRepository, times(1)).save(measureCaptor.capture());
  }

  @Test
  void testUpdateGroupReturnsExceptionForResourceNotFound() {
    assertThrows(
        ResourceNotFoundException.class,
        () -> groupService.createOrUpdateGroup(group2, "testid", "test.user"));
  }

  @Test
  void testUpdateGroupReturnsInvalidDraftStatusException() {
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    assertThrows(
        InvalidDraftStatusException.class,
        () -> groupService.createOrUpdateGroup(group2, "testid", "test.user"));
  }

  @Test
  void testCreateGroupReturnsInvalidDraftStatusException() {
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    assertThrows(
        InvalidDraftStatusException.class,
        () -> groupService.createOrUpdateGroup(group2, "testid", "test.user"));
  }

  @Test
  public void testCreateGroupWithEmptyElm() throws JsonProcessingException {
    group2.setPopulations(null);
    group2.setPopulationBasis("Boolean");
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doThrow(new JsonProcessingException("invalid elm json") {})
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypes(any(Group.class), anyString());

    measure.setElmJson("");
    assertThrows(
        IllegalArgumentException.class,
        () -> groupService.createOrUpdateGroup(group2, measure.getId(), "test.user"));
  }

  @Test
  public void testUpdateGroupWithNoFunctions() {
    measure.setGroups(List.of(group1));
    measure.setElmJson(getData("/test_elm_no_functions.json"));
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    Group group = groupService.createOrUpdateGroup(group1, measure.getId(), "test.user");
    assertNotNull(group);
  }

  @Test
  public void updateTestCaseGroupToAddMeasurePopulationsAndStratification() {
    // measure group with 4 populations and 2 stratification
    Group measureGroup = ratioGroup;
    TestCaseGroupPopulation testCaseGroup = buildTestCaseRatioGroup();
    // no testcase stratification
    testCaseGroup.setStratificationValues(null);

    // before updates
    assertEquals(5, testCaseGroup.getPopulationValues().size());
    assertNull(testCaseGroup.getStratificationValues());
    groupService.updateTestCaseGroupWithMeasureGroup(
        testCaseGroup, measureGroup, ModelType.QI_CORE.getValue());
    // after updates
    assertEquals(6, testCaseGroup.getPopulationValues().size());
    assertEquals(2, testCaseGroup.getStratificationValues().size());
  }

  @Test
  public void updateTestCaseGroupToAddMeasurePopulationsHandlesNoAssociation() {
    // measure group with 4 populations and 2 stratification
    Group measureGroup = ratioGroup.toBuilder().build();
    measureGroup.setStratifications(
        measureGroup.getStratifications().stream()
            .map(
                stratification -> {
                  Stratification strat = new Stratification();
                  strat.setId(stratification.getId());
                  strat.setCqlDefinition(stratification.getCqlDefinition());
                  strat.setDescription(stratification.getDescription());
                  strat.setAssociation(null);
                  return strat;
                })
            .collect(Collectors.toList()));
    TestCaseGroupPopulation testCaseGroup = buildTestCaseRatioGroup();
    // no testcase stratification
    testCaseGroup.setStratificationValues(null);

    // before updates
    assertEquals(5, testCaseGroup.getPopulationValues().size());
    assertNull(testCaseGroup.getStratificationValues());
    groupService.updateTestCaseGroupWithMeasureGroup(
        testCaseGroup, measureGroup, ModelType.QI_CORE.getValue());
    // after updates
    assertEquals(6, testCaseGroup.getPopulationValues().size());
    assertEquals(2, testCaseGroup.getStratificationValues().size());
  }

  @Test
  public void updateTestCaseGroupToRemoveMeasurePopulationsAndStratification() {
    // measure group with 4 populations and 2 stratification
    Group measureGroup = ratioGroup;

    // test case group with 3 populations, 2 observations and 2 stratification
    TestCaseGroupPopulation testCaseGroup = buildTestCaseRatioGroup();
    // add one more optional population so that it can be removed
    testCaseGroup
        .getPopulationValues()
        .add(buildTestCasePopulation("id-3", PopulationType.DENOMINATOR_EXCLUSION));

    // before updates
    assertEquals(6, testCaseGroup.getPopulationValues().size());
    assertEquals(2, testCaseGroup.getStratificationValues().size());

    // unselect 1 population and 1 stratification from measure group
    measureGroup.getPopulations().get(2).setDefinition(null);
    measureGroup.getStratifications().get(1).setCqlDefinition(null);
    groupService.updateTestCaseGroupWithMeasureGroup(
        testCaseGroup, measureGroup, ModelType.QI_CORE.getValue());
    // after updates
    assertEquals(5, testCaseGroup.getPopulationValues().size());
    assertEquals(1, testCaseGroup.getStratificationValues().size());
    // removed population DENOMINATOR_EXCLUSION is no longer in testCaseGroup
    TestCasePopulationValue testPopulation =
        testCaseGroup.getPopulationValues().stream()
            .filter(
                p -> StringUtils.equals(p.getId(), measureGroup.getPopulations().get(2).getId()))
            .findAny()
            .orElse(null);
    assertNull(testPopulation);
    // removed stratification is no longer in testCaseGroup
    TestCaseStratificationValue testStrata =
        testCaseGroup.getStratificationValues().stream()
            .filter(
                s ->
                    StringUtils.equals(s.getId(), measureGroup.getStratifications().get(1).getId()))
            .findAny()
            .orElse(null);
    assertNull(testStrata);
  }

  @Test
  public void updateTestCaseGroupToModifyObservations() {
    // measure group with 4 populations and 2 stratification
    Group measureGroup =
        Group.builder()
            .scoring("Ratio")
            .populationBasis("Encounter")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1"),
                    new Population(
                        "id-2",
                        PopulationType.DENOMINATOR,
                        "Denominator",
                        null,
                        null,
                        "Denominator_1"),
                    new Population(
                        "id-3",
                        PopulationType.DENOMINATOR_EXCLUSION,
                        "Denominator",
                        null,
                        null,
                        "DenominatorExclusion_1"),
                    new Population(
                        "id-4", PopulationType.NUMERATOR, "Numerator", null, null, "Numerator_1"),
                    new Population(
                        "id-4",
                        PopulationType.NUMERATOR_EXCLUSION,
                        "",
                        null,
                        null,
                        "NumeratorExlusion_1")))
            .measureObservations(
                new ArrayList<>(
                    List.of(
                        new MeasureObservation(
                            "mo-id-1",
                            "Denominator MO",
                            "a description of Numerator MO",
                            "id-2",
                            AggregateMethodType.MAXIMUM.getValue(),
                            "MeasureObservation_1_1"),
                        new MeasureObservation(
                            "mo-id-2",
                            "Numerator MO",
                            "a description of Numerator MO",
                            "id-4",
                            AggregateMethodType.MAXIMUM.getValue(),
                            "MeasureObservation_1_2"))))
            .stratifications(List.of(strata1))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    // test case group with 3 populations, 2 observations and 2 stratification
    TestCaseGroupPopulation testCaseGroup = buildTestCaseRatioGroup();
    // add one more optional population so that it can be removed
    testCaseGroup
        .getPopulationValues()
        .add(buildTestCasePopulation("id-3", PopulationType.DENOMINATOR_EXCLUSION));

    // before updates
    assertEquals(6, testCaseGroup.getPopulationValues().size());
    assertEquals(2, testCaseGroup.getStratificationValues().size());

    // unselect 1 population and 1 stratification from measure group
    measureGroup.getPopulations().get(2).setDefinition(null);
    measureGroup.getStratifications().get(0).setCqlDefinition(null);
    groupService.updateTestCaseGroupWithMeasureGroup(
        testCaseGroup, measureGroup, ModelType.QI_CORE.getValue());
    // after updates
    assertEquals(5, testCaseGroup.getPopulationValues().size());
    assertEquals(0, testCaseGroup.getStratificationValues().size());
    // removed population DENOMINATOR_EXCLUSION is no longer in testCaseGroup
    TestCasePopulationValue testPopulation =
        testCaseGroup.getPopulationValues().stream()
            .filter(
                p -> StringUtils.equals(p.getId(), measureGroup.getPopulations().get(2).getId()))
            .findAny()
            .orElse(null);
    assertNull(testPopulation);
    // removed stratification is no longer in testCaseGroup
    TestCaseStratificationValue testStrata =
        testCaseGroup.getStratificationValues().stream()
            .filter(
                s ->
                    StringUtils.equals(s.getId(), measureGroup.getStratifications().get(1).getId()))
            .findAny()
            .orElse(null);
    assertNull(testStrata);
  }

  @Test
  public void updateTestCaseGroupToRemoveAllStratificationAndAnObservations() {
    // measure group with 4 populations, 2 observations and 2 stratification
    Group measureGroup = ratioGroup;
    TestCaseGroupPopulation testCaseGroup = buildTestCaseRatioGroup();

    // before updates
    assertEquals(5, testCaseGroup.getPopulationValues().size());
    assertEquals(2, testCaseGroup.getStratificationValues().size());

    // update measure group to remove stratification and observations
    measureGroup.setStratifications(null);
    measureGroup.setMeasureObservations(null);
    groupService.updateTestCaseGroupWithMeasureGroup(
        testCaseGroup, measureGroup, ModelType.QI_CORE.getValue());
    // after updates
    assertEquals(4, testCaseGroup.getPopulationValues().size());
    assertEquals(0, testCaseGroup.getStratificationValues().size());
  }

  @Test
  public void updateTestCaseGroupToRemoveDenominatorObservation() {
    // measure group with 4 populations and 2 stratification
    Group measureGroup = ratioGroup;
    TestCaseGroupPopulation testCaseGroup = buildTestCaseRatioGroup();
    // before updates
    assertEquals(5, testCaseGroup.getPopulationValues().size());
    // remove denominator observation from measure group
    measureGroup.getMeasureObservations().remove(0);
    groupService.updateTestCaseGroupWithMeasureGroup(
        testCaseGroup, measureGroup, ModelType.QI_CORE.getValue());
    // after updates
    assertEquals(5, testCaseGroup.getPopulationValues().size());
  }

  @Test
  public void updateTestCaseGroupToRemoveNumeratorObservation() {
    // measure group with 4 populations and 2 stratification
    Group measureGroup = ratioGroup;
    TestCaseGroupPopulation testCaseGroup = buildTestCaseRatioGroup();
    // before updates
    assertEquals(5, testCaseGroup.getPopulationValues().size());
    // remove denominator observation from measure group
    measureGroup.getMeasureObservations().remove(1);
    groupService.updateTestCaseGroupWithMeasureGroup(
        testCaseGroup, measureGroup, ModelType.QI_CORE.getValue());
    // after updates
    assertEquals(5, testCaseGroup.getPopulationValues().size());
  }

  @Test
  public void testCVObservationsAreUnchangedOnPopulationChange() {
    group2.setStratifications(null);
    TestCaseGroupPopulation testCaseGroup = buildTestCaseCVGroup();
    // before updates
    assertEquals(4, testCaseGroup.getPopulationValues().size());
    groupService.updateTestCaseGroupWithMeasureGroup(
        testCaseGroup, group2, ModelType.QI_CORE.getValue());
    // after updates
    assertEquals(4, testCaseGroup.getPopulationValues().size());
  }

  @Test
  public void testRemoveGroupFromTestCases() {
    TestCaseGroupPopulation cvGroup = buildTestCaseCVGroup();
    TestCaseGroupPopulation ratioGroup = buildTestCaseRatioGroup();
    ratioGroup.setGroupId("id-2");
    List<TestCase> testCases =
        List.of(TestCase.builder().groupPopulations(List.of(cvGroup, ratioGroup)).build());
    measure.setTestCases(testCases);
    // before removal
    assertEquals(2, testCases.get(0).getGroupPopulations().size());
    groupService.removeGroupFromTestCases(ratioGroup.getGroupId(), testCases);
    // after removal
    assertEquals(1, testCases.get(0).getGroupPopulations().size());
    assertEquals(cvGroup.getGroupId(), testCases.get(0).getGroupPopulations().get(0).getGroupId());
  }

  @Test
  public void testRemoveGroupFromTestCasesNoGroupIdOrTestCaseProvided() {
    TestCaseGroupPopulation cvGroup = buildTestCaseCVGroup();
    TestCaseGroupPopulation ratioGroup = buildTestCaseRatioGroup();
    ratioGroup.setGroupId("id-2");
    List<TestCase> testCases =
        List.of(TestCase.builder().groupPopulations(List.of(cvGroup, ratioGroup)).build());
    measure.setTestCases(testCases);
    // before removal
    assertEquals(2, testCases.get(0).getGroupPopulations().size());
    groupService.removeGroupFromTestCases(null, List.of());
    // after removal
    assertEquals(2, testCases.get(0).getGroupPopulations().size());
  }

  private TestCaseGroupPopulation buildTestCaseRatioGroup() {
    List<TestCasePopulationValue> populations =
        List.of(
            buildTestCasePopulation("id-1", PopulationType.INITIAL_POPULATION),
            buildTestCasePopulation("id-2", PopulationType.DENOMINATOR),
            buildTestCasePopulation("id-4", PopulationType.NUMERATOR),
            buildTestCasePopulation(
                "denominatorObservation1", PopulationType.DENOMINATOR_OBSERVATION),
            buildTestCasePopulation("numeratorObservation1", PopulationType.NUMERATOR_OBSERVATION));
    TestCaseStratificationValue testStrata1 = buildTestCaseStrata();
    testStrata1.setId(strata1.getId());
    TestCaseStratificationValue testStrata2 = buildTestCaseStrata();
    testStrata2.setId(strata1.getId());
    List<TestCaseStratificationValue> stratification =
        new ArrayList<>(List.of(testStrata1, testStrata2));
    // test case group with 3 populations and 2 stratification
    return buildTestCaseGroup(populations, stratification);
  }

  private TestCaseGroupPopulation buildTestCaseCVGroup() {
    List<TestCasePopulationValue> populations =
        List.of(
            buildTestCasePopulation("id-1", PopulationType.INITIAL_POPULATION),
            buildTestCasePopulation("id-2", PopulationType.MEASURE_POPULATION),
            buildTestCasePopulation("id-4", PopulationType.MEASURE_OBSERVATION),
            buildTestCasePopulation("id-5", PopulationType.MEASURE_OBSERVATION));
    // test case group with 3 populations and no stratification
    return buildTestCaseGroup(populations, null);
  }

  private TestCaseGroupPopulation buildTestCaseGroup(
      List<TestCasePopulationValue> populations, List<TestCaseStratificationValue> stratification) {
    // test case group with 3 populations and 2 stratification
    return TestCaseGroupPopulation.builder()
        .groupId("group-1")
        .scoring(MeasureScoring.RATIO.toString())
        .populationBasis("boolean")
        .populationValues(new ArrayList<>(populations))
        .stratificationValues(stratification)
        .build();
  }

  private TestCaseStratificationValue buildTestCaseStrata() {
    return TestCaseStratificationValue.builder().expected(null).actual(null).build();
  }

  private TestCasePopulationValue buildTestCasePopulation(
      String id, PopulationType populationType) {
    return TestCasePopulationValue.builder().id(id).name(populationType).expected(true).build();
  }

  @Test
  public void testCreateGroupQdmMeasure() {
    String elmJson = getData("/test_elm_with_boolean.json");
    Measure qdmMeasure =
        QdmMeasure.builder()
            .id("testId")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .model(ModelType.QDM_5_6.getValue())
            .scoring(MeasureScoring.COHORT.toString())
            .patientBasis(false)
            .elmJson(elmJson)
            .build();

    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(qdmMeasure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(qdmMeasure).when(measureRepository).save(any(Measure.class));

    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    Population population =
        Population.builder()
            .id("testId1")
            .name(PopulationType.INITIAL_POPULATION)
            .definition("ipp")
            .build();
    Group qdmGroup =
        Group.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .populations(Arrays.asList(population))
            .build();
    Group persistedGroup = groupService.createOrUpdateGroup(qdmGroup, measure.getId(), "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    assertEquals(qdmGroup.getId(), persistedGroup.getId());
    Measure savedMeasure = measureCaptor.getValue();
    assertNotNull(savedMeasure.getGroups());
    assertEquals(1, savedMeasure.getGroups().size());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    assertEquals("Cohort", capturedGroup.getScoring());
    assertEquals(
        PopulationType.INITIAL_POPULATION, capturedGroup.getPopulations().get(0).getName());
    assertEquals("ipp", capturedGroup.getPopulations().get(0).getDefinition());
  }

  @Test
  public void testHandleQdmGroupReturnTypesNonPatientBasisSuccess() {
    Population population =
        Population.builder()
            .id("testId")
            .name(PopulationType.INITIAL_POPULATION)
            .definition("ipp")
            .build();
    Group qdmGroup =
        Group.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .populations(Arrays.asList(population))
            .build();
    String elmJson = getData("/test_elm_with_boolean.json");
    Measure qdmMeasure =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .patientBasis(false)
            .elmJson(elmJson)
            .build();
    assertDoesNotThrow(() -> groupService.handleQdmGroupReturnTypes(qdmGroup, qdmMeasure));
  }

  @Test
  public void testHandleQdmGroupReturnTypesNonPatientBasisThrowsException()
      throws JsonProcessingException {
    Population population =
        Population.builder()
            .id("testId")
            .name(PopulationType.INITIAL_POPULATION)
            .definition("ipp")
            .build();
    Group qdmGroup =
        Group.builder()
            .scoring(MeasureScoring.COHORT.toString())
            .populations(Arrays.asList(population))
            .build();
    String elmJson = "{ curroped: json";
    Measure qdmMeasure =
        QdmMeasure.builder()
            .model(ModelType.QDM_5_6.getValue())
            .patientBasis(false)
            .elmJson(elmJson)
            .build();

    doThrow(new JsonProcessingException("invalid elm json") {})
        .when(cqlDefinitionReturnTypeService)
        .validateCqlDefinitionReturnTypesForQdm(any(Group.class), anyString(), any(Boolean.class));

    assertThrows(
        IllegalArgumentException.class,
        () -> groupService.handleQdmGroupReturnTypes(qdmGroup, qdmMeasure),
        "Invalid elm json");
  }

  @Test
  public void testUpdateTestCaseStratificationForAddedGroupPopulation() {

    List<TestCasePopulationValue> testCasePopulationValues = new ArrayList<>();

    testCasePopulationValues.add(testCasePopulationValue1);
    testCasePopulationValues.add(testCasePopulationValue2);
    testCasePopulationValues.add(testCasePopulationValue3);

    List<TestCaseStratificationValue> testCaseStratificationValues = new ArrayList<>();
    List<TestCasePopulationValue> testCaseStrataPopulationValues = new ArrayList<>();
    testCaseStrataPopulationValues.add(testCasePopulationValue1);
    testCaseStrataPopulationValues.add(testCasePopulationValue2);
    TestCaseStratificationValue testCaseStratificationValue1 =
        TestCaseStratificationValue.builder()
            .id("stratId")
            .name("Strata-1")
            .expected(Boolean.TRUE)
            .actual(Boolean.FALSE)
            .populationValues(testCaseStrataPopulationValues)
            .build();
    testCaseStratificationValues.add(testCaseStratificationValue1);

    TestCaseGroupPopulation testCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("testGroupId")
            .scoring("Proportion")
            .populationBasis("true")
            .populationValues(testCasePopulationValues)
            .stratificationValues(testCaseStratificationValues)
            .build();

    TestCaseStratificationValue testCaseStratificationValue =
        groupService.updateTestCaseStratification(
            stratification, testCaseGroupPopulation, "Strata-1", ModelType.QDM_5_6.getValue());
    assertTrue(testCaseStratificationValue != null);
    assertEquals(testCaseStratificationValue.getPopulationValues().size(), 3);
  }

  @Test
  public void testUpdateTestCaseStratificationForDeletedGroupPopulation() {

    List<TestCasePopulationValue> testCasePopulationValues = new ArrayList<>();

    testCasePopulationValues.add(testCasePopulationValue1);
    testCasePopulationValues.add(testCasePopulationValue2);
    testCasePopulationValues.add(testCasePopulationValue3);

    List<TestCaseStratificationValue> testCaseStratificationValues = new ArrayList<>();
    List<TestCasePopulationValue> testCaseStrataPopulationValues = new ArrayList<>();
    testCaseStrataPopulationValues.add(testCasePopulationValue1);
    testCaseStrataPopulationValues.add(testCasePopulationValue2);
    testCaseStrataPopulationValues.add(testCasePopulationValue3);
    testCaseStrataPopulationValues.add(testCasePopulationValue4);

    TestCaseStratificationValue testCaseStratificationValue1 =
        TestCaseStratificationValue.builder()
            .id("stratId")
            .name("Strata-1")
            .expected(Boolean.TRUE)
            .actual(Boolean.FALSE)
            .populationValues(testCaseStrataPopulationValues)
            .build();
    testCaseStratificationValues.add(testCaseStratificationValue1);

    TestCaseGroupPopulation testCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("testGroupId")
            .scoring("Proportion")
            .populationBasis("true")
            .populationValues(testCasePopulationValues)
            .stratificationValues(testCaseStratificationValues)
            .build();

    TestCaseStratificationValue testCaseStratificationValue =
        groupService.updateTestCaseStratification(
            stratification, testCaseGroupPopulation, "Strata-1", ModelType.QDM_5_6.getValue());
    assertTrue(testCaseStratificationValue != null);
    assertEquals(testCaseStratificationValue.getPopulationValues().size(), 3);
  }

  @Test
  public void testUpdateTestCaseStratificationForChangedGroupPopulation() {

    List<TestCasePopulationValue> testCasePopulationValues = new ArrayList<>();

    testCasePopulationValues.add(testCasePopulationValue1);
    testCasePopulationValues.add(testCasePopulationValue2);

    List<TestCaseStratificationValue> testCaseStratificationValues = new ArrayList<>();
    List<TestCasePopulationValue> testCaseStrataPopulationValues = new ArrayList<>();
    testCaseStrataPopulationValues.add(testCasePopulationValue2);
    testCaseStrataPopulationValues.add(testCasePopulationValue3);

    TestCaseStratificationValue testCaseStratificationValue1 =
        TestCaseStratificationValue.builder()
            .id("stratId")
            .name("Strata-1")
            .expected(Boolean.TRUE)
            .actual(Boolean.FALSE)
            .populationValues(testCaseStrataPopulationValues)
            .build();
    testCaseStratificationValues.add(testCaseStratificationValue1);

    TestCaseGroupPopulation testCaseGroupPopulation =
        TestCaseGroupPopulation.builder()
            .groupId("testGroupId")
            .scoring("Proportion")
            .populationBasis("true")
            .populationValues(testCasePopulationValues)
            .stratificationValues(testCaseStratificationValues)
            .build();

    TestCaseStratificationValue testCaseStratificationValue =
        groupService.updateTestCaseStratification(
            stratification, testCaseGroupPopulation, "Strata-1", ModelType.QDM_5_6.getValue());
    assertTrue(testCaseStratificationValue != null);
    assertEquals(testCaseStratificationValue.getPopulationValues().size(), 2);
    assertNotEquals(
        testCaseStratificationValue.getPopulationValues().get(0).getId(),
        testCasePopulationValue3.getId());
    assertNotEquals(
        testCaseStratificationValue.getPopulationValues().get(1).getId(),
        testCasePopulationValue3.getId());
  }

  @Test
  public void testUpdateTestCaseStratificationForNonTestCasePopulationValuesFromGroup() {
    TestCaseStratificationValue testCaseStratificationValue =
        groupService.updateTestCaseStratification(
            stratification,
            new TestCaseGroupPopulation(),
            "Strata-1",
            ModelType.QI_CORE.getValue());
    assertNull(testCaseStratificationValue.getPopulationValues());
  }

  @Test
  public void testCreateStratificationOnly() {
    group1.setId("group1");
    measure.getGroups().add(group1);
    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(measure).when(measureRepository).save(any(Measure.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    groupService.createOrUpdateStratification(
        group1.getId(), measure.getId(), strata1, "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    assertNotNull(savedMeasure.getGroups());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    assertNotNull(capturedGroup.getStratifications());
    Stratification capturedStratification = capturedGroup.getStratifications().get(0);
    assertEquals("Initial Population", capturedStratification.getCqlDefinition());
    assertEquals(PopulationType.INITIAL_POPULATION, capturedStratification.getAssociation());
    assertEquals(1, capturedStratification.getAssociations().size());
    assertTrue(
        capturedStratification.getAssociations().contains(PopulationType.INITIAL_POPULATION));
  }

  @Test
  public void testCreateStratificationMultiple() {

    Stratification strata2 =
        Stratification.builder()
            .id("test-strat")
            .cqlDefinition("Initial Population")
            .association(PopulationType.INITIAL_POPULATION)
            .associations(List.of(PopulationType.INITIAL_POPULATION, PopulationType.DENOMINATOR))
            .build();

    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(measure).when(measureRepository).save(any(Measure.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    groupService.createOrUpdateStratification(
        group2.getId(), measure.getId(), strata2, "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    assertNotNull(capturedGroup.getStratifications());
    assertEquals(2, capturedGroup.getStratifications().size());
    Stratification capturedStratification = capturedGroup.getStratifications().get(1);
    assertEquals("Initial Population", capturedStratification.getCqlDefinition());
    assertEquals(PopulationType.INITIAL_POPULATION, capturedStratification.getAssociation());
    assertEquals(2, capturedStratification.getAssociations().size());
    assertTrue(
        capturedStratification.getAssociations().contains(PopulationType.INITIAL_POPULATION));
    assertTrue(capturedStratification.getAssociations().contains(PopulationType.DENOMINATOR));
  }

  @Test
  public void testUpdateStratification() {
    Stratification strata2 =
        Stratification.builder()
            .id(strata1.getId())
            .cqlDefinition("Initial Population")
            .association(PopulationType.INITIAL_POPULATION)
            .associations(List.of(PopulationType.INITIAL_POPULATION, PopulationType.DENOMINATOR))
            .build();

    ArgumentCaptor<Measure> measureCaptor = ArgumentCaptor.forClass(Measure.class);
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    doReturn(measure).when(measureRepository).save(any(Measure.class));

    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    // before update
    assertEquals(
        "FactorialOfFive", measure.getGroups().get(0).getPopulations().get(0).getDefinition());

    groupService.createOrUpdateStratification(
        group2.getId(), measure.getId(), strata2, "test.user");

    verify(measureRepository, times(1)).save(measureCaptor.capture());
    Measure savedMeasure = measureCaptor.getValue();
    assertEquals(measure.getLastModifiedBy(), savedMeasure.getLastModifiedBy());
    assertEquals(measure.getLastModifiedAt(), savedMeasure.getLastModifiedAt());
    Group capturedGroup = savedMeasure.getGroups().get(0);
    assertNotNull(capturedGroup.getStratifications());
    assertEquals(1, capturedGroup.getStratifications().size());
    Stratification capturedStratification = capturedGroup.getStratifications().get(0);
    assertEquals("Initial Population", capturedStratification.getCqlDefinition());
    assertEquals(PopulationType.INITIAL_POPULATION, capturedStratification.getAssociation());
    assertEquals(2, capturedStratification.getAssociations().size());
    assertTrue(
        capturedStratification.getAssociations().contains(PopulationType.INITIAL_POPULATION));
    assertTrue(capturedStratification.getAssociations().contains(PopulationType.DENOMINATOR));
  }

  @Test
  void testUpdateStratificationReturnsExceptionForResourceNotFound() {
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            groupService.createOrUpdateStratification(
                group2.getId(), "testid", strata1, "test.user"));
  }

  @Test
  void testUpdateStratificationReturnsInvalidDraftStatusException() {
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    assertThrows(
        InvalidDraftStatusException.class,
        () ->
            groupService.createOrUpdateStratification(
                group2.getId(), "testid", strata1, "test.user"));
  }

  @Test
  void testCreateStratificationReturnsInvalidDraftStatusException() {
    measure.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    Optional<Measure> optional = Optional.of(measure);
    doReturn(optional).when(measureRepository).findById(any(String.class));

    assertThrows(
        InvalidDraftStatusException.class,
        () ->
            groupService.createOrUpdateStratification(
                group2.getId(), "testid", strata1, "test.user"));
  }

  @Test
  void testDeleteStratification() {
    when(measureService.findMeasureById(anyString())).thenReturn(measure);

    doReturn(measure).when(measureRepository).save(any(Measure.class));

    Measure output =
        groupService.deleteStratification(
            measure.getId(), group2.getId(), strata1.getId(), "test.user");

    assertEquals(0, output.getGroups().get(0).getStratifications().size());
  }

  @Test
  void testDeleteStratificationReturnsExceptionForNullMeasureId() {
    assertThrows(
        InvalidIdException.class,
        () ->
            groupService.deleteStratification("", "grouptestid", "stratificationId", "OtherUser"));
  }

  @Test
  void testDeleteStratificationThrowsAccessException() {
    String groupId = "testgroupid";
    final Measure measure =
        Measure.builder()
            .id("measure-id")
            .createdBy("OtherUser")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .measureSet(MeasureSet.builder().owner("OtherUser").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    doThrow(new UnauthorizedException("Measure", measure.getId(), "user2"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));
    assertThrows(
        UnauthorizedException.class,
        () ->
            groupService.deleteStratification("measure-id", groupId, "stratificationId", "user2"));
  }

  @Test
  void testDeleteStratificationReturnsInvalidDraftStatusException() {
    String groupId = "testgroupid";
    final Measure measure =
        Measure.builder()
            .id("measure-id")
            .createdBy("OtherUser")
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .measureSet(MeasureSet.builder().owner("OtherUser").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);
    assertThrows(
        InvalidDraftStatusException.class,
        () ->
            groupService.deleteStratification("measure-id", groupId, "stratificationId", "user2"));
  }

  @Test
  void testDeleteStratificationReturnsExceptionForResourceNotFound() {
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            groupService.deleteStratification(
                "testid", "testgroupid", "stratificationId", "user2"));
  }

  @Test
  void testDeleteStratificationReturnsExceptionForNullId() {
    final Measure measure =
        Measure.builder()
            .id("measure-id")
            .createdBy("OtherUser")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .measureSet(MeasureSet.builder().owner("OtherUser").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(measure);

    assertThrows(
        InvalidIdException.class,
        () -> groupService.deleteStratification("measure-id", "", "stratificationId", "OtherUser"));
  }

  @Test
  void testDeleteStratificationReturnsExceptionForGroupNotFoundInMeasure() {
    Group group =
        Group.builder()
            .id("testgroupid")
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "InitialPopulation_1")))
            .build();

    Measure existingMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .groups(List.of(group))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .measureSet(MeasureSet.builder().owner("test.user").build())
            .build();
    when(measureService.findMeasureById(anyString())).thenReturn(existingMeasure);

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            groupService.deleteStratification(
                "measure-id", "grouptestid1", "stratificationId", "test.user"));
  }

  @Test
  void testDeleteStratificationReturnsExceptionForStratificationNotFoundInMeasure() {
    measure.getGroups().get(0).setStratifications(Collections.emptyList());

    when(measureService.findMeasureById(anyString())).thenReturn(measure);

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            groupService.deleteStratification(
                measure.getId(), group2.getId(), strata1.getId(), "test.user"));
  }

  @Test
  void testAddCompositeGroup() {
    Group compositeGroup =
        Group.builder()
            .id("composite-group-id")
            .scoring(MeasureScoring.COMPOSITE.toString())
            .compositeScoring(CompositeMeasureScoring.OPPORTUNITY.toString())
            .populations(new ArrayList<>())
            .build();

    List<Group> groups = new ArrayList<>();
    groups.add(group2);

    Measure measure =
        this.measure.toBuilder()
            .measureMetaData(MeasureMetaData.builder().draft(true).composite(true).build())
            .groups(groups)
            .build();

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    assertEquals(1, measure.getGroups().size());

    Group updatedGroup =
        groupService.createOrUpdateGroup(compositeGroup, measure.getId(), "test.user");

    assertEquals(2, measure.getGroups().size());
    assertEquals(measure.getGroups().get(0).getId(), group2.getId());
    assertEquals(MeasureScoring.COMPOSITE.toString(), updatedGroup.getScoring());
    assertEquals(
        CompositeMeasureScoring.OPPORTUNITY.toString(), updatedGroup.getCompositeScoring());
  }

  @Test
  void testUpdateCompositeGroup() {
    Group compositeGroup =
        Group.builder()
            .id("composite-group-id")
            .scoring(MeasureScoring.COMPOSITE.toString())
            .compositeScoring(CompositeMeasureScoring.OPPORTUNITY.toString())
            .populations(new ArrayList<>())
            .build();

    Group modifiedCompositeGroup =
        Group.builder()
            .id("composite-group-id")
            .scoring(MeasureScoring.COMPOSITE.toString())
            .compositeScoring(CompositeMeasureScoring.LINEAR.toString())
            .populations(new ArrayList<>())
            .build();

    Measure measureWithCompositeGroup =
        measure.toBuilder()
            .measureMetaData(MeasureMetaData.builder().draft(true).composite(true).build())
            .groups(List.of(compositeGroup))
            .build();

    when(measureRepository.findById(anyString()))
        .thenReturn(Optional.of(measureWithCompositeGroup));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));

    Group updatedGroup =
        groupService.createOrUpdateGroup(
            modifiedCompositeGroup, measureWithCompositeGroup.getId(), "test.user");

    assertEquals(MeasureScoring.COMPOSITE.toString(), updatedGroup.getScoring());
    assertEquals(CompositeMeasureScoring.LINEAR.toString(), updatedGroup.getCompositeScoring());
    assertEquals(1, measureWithCompositeGroup.getGroups().size());
  }

  @Test
  void testThrowsWhenAddingCompositeGroupToNonCompositeMeasure() {
    Group compositeGroup =
        Group.builder()
            .id("composite-group-id")
            .scoring(MeasureScoring.COMPOSITE.toString())
            .compositeScoring(CompositeMeasureScoring.OPPORTUNITY.toString())
            .populations(new ArrayList<>())
            .build();

    Measure measureWithCompositeGroup =
        measure.toBuilder()
            .measureMetaData(MeasureMetaData.builder().draft(true).composite(false).build())
            .groups(new ArrayList<>())
            .build();

    when(measureRepository.findById(anyString()))
        .thenReturn(Optional.of(measureWithCompositeGroup));

    assertThrows(
        InvalidRequestException.class,
        () ->
            groupService.createOrUpdateGroup(
                compositeGroup, measureWithCompositeGroup.getId(), "test.user"));
  }

  @Test
  void testThrowsWhenAddingCompositeGroupWithNoCompositeScoring() {
    Group compositeGroup =
        Group.builder()
            .id("composite-group-id")
            .scoring(MeasureScoring.COMPOSITE.toString())
            .populations(new ArrayList<>())
            .build();

    Measure measureWithCompositeGroup =
        measure.toBuilder()
            .measureMetaData(MeasureMetaData.builder().draft(true).composite(true).build())
            .groups(new ArrayList<>())
            .build();

    when(measureRepository.findById(anyString()))
        .thenReturn(Optional.of(measureWithCompositeGroup));

    assertThrows(
        InvalidRequestException.class,
        () ->
            groupService.createOrUpdateGroup(
                compositeGroup, measureWithCompositeGroup.getId(), "test.user"));
  }
}
