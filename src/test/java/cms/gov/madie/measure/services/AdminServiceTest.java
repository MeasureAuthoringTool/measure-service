package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.DeleteMeasuresByOwnersResult;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.ActionLogRepositoryImpl;
import cms.gov.madie.measure.repositories.ExportRepository;
import cms.gov.madie.measure.repositories.MeasureLockRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.repositories.TestCaseLockRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import gov.cms.madie.models.measure.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

  @Mock private MeasureService measureService;
  @Mock private MeasureRepository measureRepository;
  @Mock private MeasureSetRepository measureSetRepository;
  @Mock private ExportRepository exportRepository;
  @Mock private ActionLogRepositoryImpl actionLogRepository;
  @Mock private MongoGridFsService mongoGridFsService;
  @Mock private MeasureLockRepository measureLockRepository;
  @Mock private TestCaseLockRepository testCaseLockRepository;
  @InjectMocks private AdminService adminService;

  private final String incorrectCodeSystemValue =
      "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets";
  private final String codeSystemValue = "2.16.840.1.113883.6.285";

  @Test
  void updateCodeSystemInTestCaseJsonWhenModelIsQDM() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .model(ModelType.QDM_5_6.getValue())
            .testCases(
                List.of(
                    TestCase.builder()
                        .id("testCaseId")
                        .caseNumber(80)
                        .json(incorrectCodeSystemValue)
                        .build()))
            .build();
    when(measureService.findMeasureById("measureId")).thenReturn(measure);
    when(measureRepository.save(any(Measure.class))).thenReturn(measure);

    List<Integer> caseNumbers =
        adminService.updateCodeSystem(
            "measureId", "testUser", incorrectCodeSystemValue, codeSystemValue);

    assertThat(caseNumbers, is(notNullValue()));
    assertThat(caseNumbers.size(), is(equalTo(1)));
    assertThat(caseNumbers.get(0), is(equalTo(80)));
  }

  @Test
  void updateCodeSystemThrowsInvalidRequestExceptionWhenCodeSystemNotProvided() {
    when(measureService.findMeasureById("invalidId")).thenReturn(null);

    assertThrows(
        InvalidRequestException.class,
        () -> adminService.updateCodeSystem("invalidId", "testUser", "", codeSystemValue));
  }

  @Test
  void updateCodeSystemThrowsResourceNotFoundExceptionWhenMeasureDoesNotExist() {
    when(measureService.findMeasureById("invalidId")).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            adminService.updateCodeSystem(
                "invalidId", "testUser", incorrectCodeSystemValue, codeSystemValue));
  }

  @Test
  void updateCodeSystemThrowsErrorWhenModelIsNotQDM() {
    Measure measure = Measure.builder().id("measureId").model("FHIR").build();
    when(measureService.findMeasureById("measureId")).thenReturn(measure);

    assertThrows(
        InvalidRequestException.class,
        () ->
            adminService.updateCodeSystem(
                "measureId", "testUser", incorrectCodeSystemValue, codeSystemValue));
  }

  @Test
  void updateCodeSystemDoesNotUpdateTestCaseJsonWhenNoChangesAreRequired() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .model(ModelType.QDM_5_6.getValue())
            .testCases(
                List.of(
                    TestCase.builder()
                        .id("testCaseId")
                        .caseNumber(1)
                        .json("2.16.840.1.113883.6.285")
                        .build()))
            .build();
    when(measureService.findMeasureById("measureId")).thenReturn(measure);

    List<Integer> caseNumbers =
        adminService.updateCodeSystem(
            "measureId", "testUser", incorrectCodeSystemValue, codeSystemValue);

    assertThat(caseNumbers, is(notNullValue()));
    assertThat(caseNumbers.size(), is(equalTo(0)));
  }

  @Test
  void backfillTestCaseSetIdsThrowsInvalidResourceStateExceptionWhenNoTestCases() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of())
            .build();

    assertThrows(
        InvalidResourceStateException.class,
        () -> adminService.backfillTestCaseSetIds(measure, "testUser"));
  }

  @Test
  void
      backfillTestCaseSetIdsThrowsTestCaseSetIdsAlreadyAssignedExceptionWhenCurrentMeasureHasIds() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .model(ModelType.QI_CORE.getValue())
            .testCases(
                List.of(TestCase.builder().id("tc1").testCaseSetId(UUID.randomUUID()).build()))
            .build();

    assertThrows(
        TestCaseSetIdsAlreadyAssignedException.class,
        () -> adminService.backfillTestCaseSetIds(measure, "testUser"));
  }

  @Test
  void backfillTestCaseSetIdsThrowsUnsupportedTypeExceptionWhenAnotherMeasureInSetHasIds() {
    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of(TestCase.builder().id("tc1").build()))
            .build();

    when(measureRepository.testCaseSetIdExistsInSet("measureSetId")).thenReturn(true);

    assertThrows(
        UnsupportedTypeException.class,
        () -> adminService.backfillTestCaseSetIds(measure, "testUser"));
  }

  @Test
  void backfillTestCaseSetIdsAssignsUUIDToEachTestCaseAndSaves() {
    TestCase tc1 = TestCase.builder().id("tc1").build();
    TestCase tc2 = TestCase.builder().id("tc2").build();

    Measure measure =
        Measure.builder()
            .id("measureId")
            .measureSetId("measureSetId")
            .model(ModelType.QI_CORE.getValue())
            .testCases(List.of(tc1, tc2))
            .build();

    when(measureRepository.testCaseSetIdExistsInSet("measureSetId")).thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(measure);

    Measure result = adminService.backfillTestCaseSetIds(measure, "testUser");

    assertThat(result, is(notNullValue()));
    assertThat(tc1.getTestCaseSetId(), is(notNullValue()));
    assertThat(tc2.getTestCaseSetId(), is(notNullValue()));
    verify(measureRepository, times(1)).save(measure);
  }

  private MeasureSet measureSet(String setId, String owner) {
    return MeasureSet.builder().id(setId + "-doc").measureSetId(setId).owner(owner).build();
  }

  private Measure measureIn(String setId, String measureId, String... testCaseIds) {
    return Measure.builder()
        .id(measureId)
        .measureSetId(setId)
        .testCases(
            java.util.Arrays.stream(testCaseIds)
                .map(tcId -> TestCase.builder().id(tcId).build())
                .toList())
        .build();
  }

  @Test
  void deleteMeasuresByOwnersDeletesEverythingForTheOwners() {
    MeasureSet setOne = measureSet("set-1", "testuser1");
    MeasureSet setTwo = measureSet("set-2", "testuser2");
    Measure measureOne = measureIn("set-1", "measure-1", "tc-1", "tc-2");
    Measure measureTwo = measureIn("set-2", "measure-2", "tc-3");
    Export export =
        Export.builder()
            .id("export-1")
            .measureId("measure-1")
            .measureBundleGridFsId("grid-1")
            .measureBundleWithoutWarningsGridFsId("grid-2")
            .build();

    when(measureSetRepository.findAllByOwnerIn(List.of("testuser1", "testuser2")))
        .thenReturn(List.of(setOne, setTwo));
    when(measureRepository.findByCreatedByIn(List.of("testuser1", "testuser2")))
        .thenReturn(List.of());
    when(measureRepository.findByMeasureSetIdIn(List.of("set-1", "set-2")))
        .thenReturn(List.of(measureOne, measureTwo));
    when(exportRepository.findAllByMeasureIdIn(List.of("measure-1", "measure-2")))
        .thenReturn(List.of(export));
    when(actionLogRepository.deleteActionLogsByTargetIds(anyCollection(), eq(Measure.class)))
        .thenReturn(2L);
    when(actionLogRepository.deleteActionLogsByTargetIds(anyCollection(), eq(MeasureSet.class)))
        .thenReturn(2L);
    when(actionLogRepository.deleteActionLogsByTargetIds(anyCollection(), eq(TestCase.class)))
        .thenReturn(3L);
    when(measureLockRepository.deleteByMeasureIdIn(anyCollection())).thenReturn(1L);
    when(testCaseLockRepository.deleteByMeasureIdIn(anyCollection())).thenReturn(4L);

    DeleteMeasuresByOwnersResult result =
        adminService.deleteMeasuresByOwners(List.of("testuser1", "testuser2"), "admin");

    assertThat(result.getMeasureSetCount(), is(2));
    assertThat(result.getMeasureCount(), is(2));
    assertThat(result.getCreatedByOnlyMeasureCount(), is(0));
    assertThat(result.getExportCount(), is(1));
    assertThat(result.getExportGridFsFileCount(), is(2));
    assertThat(result.getMeasureActionLogCount(), is(2L));
    assertThat(result.getMeasureSetActionLogCount(), is(2L));
    assertThat(result.getTestCaseActionLogCount(), is(3L));
    assertThat(result.getMeasureLockCount(), is(1L));
    assertThat(result.getTestCaseLockCount(), is(4L));
    assertThat(result.getMeasureIds(), is(List.of("measure-1", "measure-2")));
    assertThat(result.getMeasureSetIds(), is(List.of("set-1", "set-2")));

    verify(mongoGridFsService).deleteById("grid-1");
    verify(mongoGridFsService).deleteById("grid-2");
    verify(exportRepository).deleteAll(List.of(export));
    verify(measureRepository).deleteAll(List.of(measureOne, measureTwo));
    verify(measureSetRepository).deleteAll(List.of(setOne, setTwo));
    verify(measureLockRepository).deleteByMeasureIdIn(List.of("measure-1", "measure-2"));
    verify(testCaseLockRepository).deleteByMeasureIdIn(List.of("measure-1", "measure-2"));
  }

  @Test
  void deleteMeasuresByOwnersScopesActionLogsToTheDeletedRecords() {
    when(measureSetRepository.findAllByOwnerIn(List.of("testuser1")))
        .thenReturn(List.of(measureSet("set-1", "testuser1")));
    when(measureRepository.findByMeasureSetIdIn(List.of("set-1")))
        .thenReturn(List.of(measureIn("set-1", "measure-1", "tc-1", "tc-2")));
    when(measureRepository.findByCreatedByIn(List.of("testuser1"))).thenReturn(List.of());
    when(exportRepository.findAllByMeasureIdIn(List.of("measure-1"))).thenReturn(List.of());

    adminService.deleteMeasuresByOwners(List.of("testuser1"), "admin");

    ArgumentCaptor<Collection<String>> targetIds = ArgumentCaptor.forClass(Collection.class);
    verify(actionLogRepository).deleteActionLogsByTargetIds(targetIds.capture(), eq(Measure.class));
    assertThat(targetIds.getValue(), is(List.of("measure-1")));

    verify(actionLogRepository)
        .deleteActionLogsByTargetIds(targetIds.capture(), eq(MeasureSet.class));
    assertThat(List.copyOf(targetIds.getValue()), is(List.of("set-1")));

    verify(actionLogRepository)
        .deleteActionLogsByTargetIds(targetIds.capture(), eq(TestCase.class));
    assertThat(targetIds.getValue(), is(List.of("tc-1", "tc-2")));
  }

  @Test
  void deleteMeasuresByOwnersNormalizesHarpIds() {
    when(measureSetRepository.findAllByOwnerIn(List.of("testuser1"))).thenReturn(List.of());
    when(measureRepository.findByCreatedByIn(List.of("testuser1"))).thenReturn(List.of());

    DeleteMeasuresByOwnersResult result =
        adminService.deleteMeasuresByOwners(List.of("  TestUser1 ", "testuser1"), "admin");

    assertThat(result.getHarpIds(), is(List.of("testuser1")));
    assertThat(result.getMeasureSetCount(), is(0));
    verify(measureRepository, never()).findByMeasureSetIdIn(any());
    verify(measureSetRepository, never()).deleteAll(any());
    verify(measureLockRepository, never()).deleteByMeasureIdIn(any());
    verify(testCaseLockRepository, never()).deleteByMeasureIdIn(any());
  }

  @Test
  void deleteMeasuresByOwnersThrowsWhenNoHarpIdsGiven() {
    assertThrows(
        InvalidRequestException.class,
        () -> adminService.deleteMeasuresByOwners(List.of(), "admin"));
    assertThrows(
        InvalidRequestException.class, () -> adminService.deleteMeasuresByOwners(null, "admin"));
    assertThrows(
        InvalidRequestException.class,
        () -> adminService.deleteMeasuresByOwners(List.of("  ", ""), "admin"));
  }

  @Test
  void deleteMeasuresByOwnersPicksUpOrphanedMeasuresByCreatedBy() {
    // measure-2 has no measure set row, so only the createdBy pass can reach it
    Measure viaSet = measureIn("set-1", "measure-1", "tc-1");
    Measure orphan = measureIn("set-gone", "measure-2", "tc-2");

    when(measureSetRepository.findAllByOwnerIn(List.of("testuser1")))
        .thenReturn(List.of(measureSet("set-1", "testuser1")));
    when(measureRepository.findByMeasureSetIdIn(List.of("set-1"))).thenReturn(List.of(viaSet));
    when(measureRepository.findByCreatedByIn(List.of("testuser1")))
        .thenReturn(List.of(viaSet, orphan));
    // "set-gone" has no MeasureSet row, so its action logs are orphans too
    when(measureSetRepository.findAllByMeasureSetIdIn(anyCollection())).thenReturn(List.of());
    when(exportRepository.findAllByMeasureIdIn(List.of("measure-1", "measure-2")))
        .thenReturn(List.of());

    DeleteMeasuresByOwnersResult result =
        adminService.deleteMeasuresByOwners(List.of("testuser1"), "admin");

    // viaSet is not double counted despite being returned by both queries
    assertThat(result.getMeasureCount(), is(2));
    assertThat(result.getCreatedByOnlyMeasureCount(), is(1));
    assertThat(result.getMeasureIds(), is(List.of("measure-1", "measure-2")));

    verify(measureRepository).deleteAll(List.of(viaSet, orphan));
    // only the owned set is deleted; "set-gone" has no row to delete
    verify(measureSetRepository).deleteAll(List.of(measureSet("set-1", "testuser1")));

    ArgumentCaptor<Collection<String>> setLogIds = ArgumentCaptor.forClass(Collection.class);
    verify(actionLogRepository)
        .deleteActionLogsByTargetIds(setLogIds.capture(), eq(MeasureSet.class));
    assertThat(List.copyOf(setLogIds.getValue()), is(List.of("set-1", "set-gone")));
  }

  @Test
  void deleteMeasuresByOwnersKeepsActionLogsOfSetsThatStillExist() {
    Measure orphan = measureIn("set-live", "measure-2", "tc-2");

    when(measureSetRepository.findAllByOwnerIn(List.of("testuser1"))).thenReturn(List.of());
    when(measureRepository.findByCreatedByIn(List.of("testuser1"))).thenReturn(List.of(orphan));
    // set-live still exists and belongs to someone else, so its history must survive
    when(measureSetRepository.findAllByMeasureSetIdIn(anyCollection()))
        .thenReturn(List.of(measureSet("set-live", "someoneelse")));
    when(exportRepository.findAllByMeasureIdIn(List.of("measure-2"))).thenReturn(List.of());

    adminService.deleteMeasuresByOwners(List.of("testuser1"), "admin");

    ArgumentCaptor<Collection<String>> setLogIds = ArgumentCaptor.forClass(Collection.class);
    verify(actionLogRepository)
        .deleteActionLogsByTargetIds(setLogIds.capture(), eq(MeasureSet.class));
    assertThat(List.copyOf(setLogIds.getValue()), is(List.of()));

    verify(measureRepository).deleteAll(List.of(orphan));
  }
}
