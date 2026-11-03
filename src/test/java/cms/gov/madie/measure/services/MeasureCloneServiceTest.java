package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.measure.FhirMeasure;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.QdmMeasure;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.measure.TestCaseGroupPopulation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasureCloneServiceTest {
  private static final String ORIGINAL_ID = "original-id";
  private static final String CLONE_ID = "clone-id";
  private static final String ADMIN = "admin";
  private static final UUID TEST_CASE_SET_ID = UUID.randomUUID();

  @Mock private MeasureRepository measureRepository;
  @Mock private MeasureService measureService;
  @Mock private MeasureSetService measureSetService;
  @Mock private VersionService versionService;
  @Mock private ActionLogService actionLogService;
  @Mock private CompositeRelationshipService compositeRelationshipService;

  @InjectMocks private MeasureCloneService measureCloneService;

  private Measure original;

  @BeforeEach
  void setUp() {
    original =
        FhirMeasure.builder()
            .id(ORIGINAL_ID)
            .measureSetId("set-id")
            .versionId("version-id")
            .version(new Version(1, 0, 0))
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .measureName("Measure")
            .cqlLibraryName("Lib")
            .ecqmTitle("Title")
            .cql("library Lib version '1.0.000'\nusing QICore version '6.0.0'")
            .createdBy("owner")
            .measureMetaData(
                MeasureMetaData.builder().draft(false).versionDate(Instant.EPOCH).build())
            .compositeMeasureIds(List.of("composite-id"))
            .groups(List.of(group("group-1"), group("group-2")))
            .testCases(
                List.of(
                    // covers only the second group
                    testCase("test-case-1", "group-2"),
                    testCase("test-case-2", "group-1", "group-2", "deleted-group")))
            .build();
  }

  private static Group group(String id) {
    return Group.builder().id(id).build();
  }

  private static TestCase testCase(String id, String... groupIds) {
    return TestCase.builder()
        .id(id)
        .testCaseSetId(TEST_CASE_SET_ID)
        .groupPopulations(
            Stream.of(groupIds)
                .map(groupId -> TestCaseGroupPopulation.builder().groupId(groupId).build())
                .toList())
        .build();
  }

  /** Mimics an insert: the measure arrives without an id and gets a new one. */
  private void stubSaveAsInsert() {
    when(measureRepository.save(any(Measure.class)))
        .thenAnswer(
            invocation -> {
              Measure measure = invocation.getArgument(0);
              assertNull(measure.getId());
              measure.setId(CLONE_ID);
              return measure;
            });
  }

  private void stubOriginalAndFreeNames() {
    when(measureRepository.findByIdAndActive(ORIGINAL_ID, true)).thenReturn(Optional.of(original));
    when(measureRepository.existsByCqlLibraryNameAndActive(anyString(), anyBoolean()))
        .thenReturn(false);
    when(measureRepository.existsByMeasureNameAndActive(anyString(), anyBoolean()))
        .thenReturn(false);
    stubSaveAsInsert();
  }

  private void stubRevalidationAndCaseNumbering() {
    when(measureService.revalidateCqlDependencies(any(Measure.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(versionService.initCaseNumbering(any(Measure.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void clonesIntoANewDraftMeasureInANewMeasureSet() {
    stubOriginalAndFreeNames();
    stubRevalidationAndCaseNumbering();

    Measure clone = measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN);

    assertEquals(CLONE_ID, clone.getId());
    assertEquals("CLONE Measure", clone.getMeasureName());
    assertEquals("CLONELib", clone.getCqlLibraryName());
    assertEquals("CLONE Title", clone.getEcqmTitle());
    assertEquals("0.0.000", clone.getVersion().toString());
    assertNotEquals("set-id", clone.getMeasureSetId());
    assertNotEquals("version-id", clone.getVersionId());
    assertEquals(ADMIN, clone.getCreatedBy());
    assertEquals(ADMIN, clone.getLastModifiedBy());
    assertTrue(clone.getMeasureMetaData().isDraft());
    assertNull(clone.getMeasureMetaData().getVersionDate());
    assertNull(clone.getCompositeMeasureIds());
    assertEquals(
        "library CLONELib version '0.0.000'\nusing QICore version '6.0.0'", clone.getCql());
    verify(measureService).revalidateCqlDependencies(clone);
    verify(measureRepository, times(1)).save(any(Measure.class));
  }

  @Test
  void regeneratesGroupAndTestCaseIdsAndRemapsTestCasesByGroupId() {
    stubOriginalAndFreeNames();
    stubRevalidationAndCaseNumbering();

    Measure clone = measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN);

    String newGroup1 = clone.getGroups().get(0).getId();
    String newGroup2 = clone.getGroups().get(1).getId();
    assertFalse(List.of("group-1", "group-2").contains(newGroup1));
    assertFalse(List.of("group-1", "group-2").contains(newGroup2));
    assertNotEquals(newGroup1, newGroup2);

    TestCase testCase1 = clone.getTestCases().get(0);
    TestCase testCase2 = clone.getTestCases().get(1);
    assertFalse(List.of("test-case-1", "test-case-2").contains(testCase1.getId()));
    assertNotEquals(testCase1.getId(), testCase2.getId());
    assertNotEquals(TEST_CASE_SET_ID, testCase1.getTestCaseSetId());
    // remapped by id, not position
    assertEquals(newGroup2, testCase1.getGroupPopulations().get(0).getGroupId());
    assertEquals(newGroup1, testCase2.getGroupPopulations().get(0).getGroupId());
    assertEquals(newGroup2, testCase2.getGroupPopulations().get(1).getGroupId());
    // a stale group id is left as is
    assertEquals("deleted-group", testCase2.getGroupPopulations().get(2).getGroupId());
  }

  @Test
  void logsCreationOfTheCloneOnlyAndSeedsItsTestCases() {
    stubOriginalAndFreeNames();
    stubRevalidationAndCaseNumbering();

    Measure clone = measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN);

    verify(actionLogService).logAction(CLONE_ID, Measure.class, ActionType.CREATED, ADMIN);
    verifyNoMoreInteractions(actionLogService);
    verify(measureSetService).createMeasureSet(ADMIN, CLONE_ID, clone.getMeasureSetId(), null);
    verify(compositeRelationshipService).addCompositeRelationships(clone, ADMIN);
    verify(versionService).initCaseNumbering(clone, clone.getTestCases());
  }

  @Test
  void usesTheFirstCloneNumberFreeForBothTheLibraryAndTheMeasureName() {
    when(measureRepository.findByIdAndActive(ORIGINAL_ID, true)).thenReturn(Optional.of(original));
    // clone 1: library taken, so the name is never checked
    when(measureRepository.existsByCqlLibraryNameAndActive("CLONELib", true)).thenReturn(true);
    // clone 2: library free, name taken
    when(measureRepository.existsByCqlLibraryNameAndActive("CLONE2Lib", true)).thenReturn(false);
    when(measureRepository.existsByMeasureNameAndActive("CLONE2 Measure", true)).thenReturn(true);
    // clone 3: both free
    when(measureRepository.existsByCqlLibraryNameAndActive("CLONE3Lib", true)).thenReturn(false);
    when(measureRepository.existsByMeasureNameAndActive("CLONE3 Measure", true)).thenReturn(false);
    stubSaveAsInsert();
    stubRevalidationAndCaseNumbering();

    Measure clone = measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN);

    assertEquals("CLONE3 Measure", clone.getMeasureName());
    assertEquals("CLONE3Lib", clone.getCqlLibraryName());
    assertTrue(clone.getCql().startsWith("library CLONE3Lib version '0.0.000'"));
    verify(measureRepository, never()).existsByMeasureNameAndActive("CLONE Measure", true);
  }

  @Test
  void cloningACloneJoinsTheSameNumberedFamily() {
    original.setMeasureName("CLONE Measure");
    original.setCqlLibraryName("CLONELib");
    original.setEcqmTitle("CLONE Title");
    original.setCql("library CLONELib version '0.0.000'");
    when(measureRepository.findByIdAndActive(ORIGINAL_ID, true)).thenReturn(Optional.of(original));
    // taken by the measure being cloned
    when(measureRepository.existsByCqlLibraryNameAndActive("CLONELib", true)).thenReturn(true);
    when(measureRepository.existsByCqlLibraryNameAndActive("CLONE2Lib", true)).thenReturn(false);
    when(measureRepository.existsByMeasureNameAndActive("CLONE2 Measure", true)).thenReturn(false);
    stubSaveAsInsert();
    stubRevalidationAndCaseNumbering();

    Measure clone = measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN);

    assertEquals("CLONE2 Measure", clone.getMeasureName());
    assertEquals("CLONE2Lib", clone.getCqlLibraryName());
    assertEquals("CLONE Title", clone.getEcqmTitle());
    assertEquals("library CLONE2Lib version '0.0.000'", clone.getCql());
  }

  @Test
  void givesUpWhenNoCloneNumberIsFree() {
    when(measureRepository.findByIdAndActive(ORIGINAL_ID, true)).thenReturn(Optional.of(original));
    when(measureRepository.existsByCqlLibraryNameAndActive(anyString(), eq(true))).thenReturn(true);

    assertThrows(
        DuplicateKeyException.class, () -> measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN));

    verify(measureRepository, times(MeasureCloneService.MAX_CLONE_NUMBER))
        .existsByCqlLibraryNameAndActive(anyString(), eq(true));
    verify(measureRepository, never()).save(any(Measure.class));
    verifyNoInteractions(actionLogService, measureSetService);
  }

  @Test
  void throwsNotFoundForAMissingOrDeletedMeasure() {
    when(measureRepository.findByIdAndActive(ORIGINAL_ID, true)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN));

    verify(measureRepository, never()).save(any(Measure.class));
    verifyNoInteractions(actionLogService, measureSetService);
  }

  @Test
  void leavesCqlThatDoesNotDeclareTheOriginalLibraryAsIs() {
    original.setCql("library Other version '0.0.000'");
    original.setElmJson("original-elm");
    stubOriginalAndFreeNames();
    when(versionService.initCaseNumbering(any(Measure.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Measure clone = measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN);

    assertEquals("library Other version '0.0.000'", clone.getCql());
    assertEquals("original-elm", clone.getElmJson());
    verify(measureService, never()).revalidateCqlDependencies(any(Measure.class));
  }

  @Test
  void leavesBlankCqlAsIs() {
    original.setCql("");
    stubOriginalAndFreeNames();
    when(versionService.initCaseNumbering(any(Measure.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Measure clone = measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN);

    assertEquals("", clone.getCql());
    verify(measureService, never()).revalidateCqlDependencies(any(Measure.class));
  }

  @Test
  void clonesAQdmMeasureWithoutTestCaseSetIds() {
    TestCase qdmTestCase = testCase("test-case-1", "group-1");
    qdmTestCase.setTestCaseSetId(null);
    original =
        QdmMeasure.builder()
            .id(ORIGINAL_ID)
            .model(ModelType.QDM_5_6.getValue())
            .measureName("Measure")
            .cqlLibraryName("Lib")
            .ecqmTitle("Title")
            .measureMetaData(MeasureMetaData.builder().build())
            .groups(List.of(group("group-1")))
            .testCases(List.of(qdmTestCase))
            .build();
    stubOriginalAndFreeNames();
    when(versionService.initCaseNumbering(any(Measure.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Measure clone = measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN);

    assertTrue(clone instanceof QdmMeasure);
    assertNull(clone.getTestCases().get(0).getTestCaseSetId());
    assertEquals(
        clone.getGroups().get(0).getId(),
        clone.getTestCases().get(0).getGroupPopulations().get(0).getGroupId());
  }

  @Test
  void skipsTestCaseSequenceWhenThereAreNoTestCases() {
    original.setTestCases(List.of());
    stubOriginalAndFreeNames();
    when(measureService.revalidateCqlDependencies(any(Measure.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Measure clone = measureCloneService.cloneMeasure(ORIGINAL_ID, ADMIN);

    assertEquals(CLONE_ID, clone.getId());
    verifyNoInteractions(versionService);
  }
}
