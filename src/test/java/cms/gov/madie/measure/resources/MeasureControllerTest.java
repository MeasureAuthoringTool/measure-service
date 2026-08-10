package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.CqlDiffResultDTO;
import cms.gov.madie.measure.dto.CqlFileComparisonDTO;
import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.services.*;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.measure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasureControllerTest {

  @Mock private MeasureRepository repository;
  @Mock private MeasureService measureService;
  @Mock private MeasureSetService measureSetService;
  @Mock private GroupService groupService;
  @Mock private ActionLogService actionLogService;
  @Mock private MeasureSetRepository measureSetRepository;
  @Mock private TestCaseService testCaseService;
  @Mock private TestCaseLockService testCaseLockService;
  @Mock private AppConfigService appConfigService;
  @Mock private CqlDifferentiatorService cqlDifferentiatorService;
  @InjectMocks private MeasureController controller;
  @Mock private Principal principal;

  private Measure measure1;
  private MeasureListDTO measureList;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;
  @Captor private ArgumentCaptor<Class> targetClassArgumentCaptor;
  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;
  @Captor private ArgumentCaptor<String> performedByArgumentCaptor;

  @BeforeEach
  public void setUp() {
    measure1 =
        Measure.builder()
            .model(ModelType.QI_CORE.toString())
            .active(true)
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .build();

    measureList =
        MeasureListDTO.builder()
            .active(true)
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .version(new Version(0, 0, 1))
            .build();
  }

  @Test
  void saveMeasure() {
    measure1.setId("testId");
    doReturn(measure1)
        .when(measureService)
        .createMeasure(any(Measure.class), anyString(), anyString(), any(Boolean.class));
    Measure measures = new Measure();
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Measure> response = controller.addMeasure(measures, false, principal, "");
    assertNotNull(response.getBody());
    assertEquals("IDIDID", response.getBody().getMeasureSetId());

    Measure savedMeasure = response.getBody();
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measure1.getMeasureName())));
    assertThat(savedMeasure.getId(), is(equalTo(measure1.getId())));
  }

  @Test
  void getMeasuresWithOwnedOwnershipType() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));
    when(measureService.getMeasuresByCriteria(
            eq(null),
            eq(List.of(OwnershipType.OWNED)),
            eq(false),
            any(Pageable.class),
            eq("test.user")))
        .thenReturn(measures);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<MeasureListDTO>> response =
        controller.getMeasures(
            principal, List.of(OwnershipType.OWNED), 10, 0, "lastModifiedAt", "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(
            eq(null),
            eq(List.of(OwnershipType.OWNED)),
            eq(false),
            any(Pageable.class),
            eq("test.user"));

    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void getMeasuresWithSharedOwnershipType() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));
    when(measureService.getMeasuresByCriteria(
            eq(null),
            eq(List.of(OwnershipType.SHARED)),
            eq(false),
            any(Pageable.class),
            eq("test.user")))
        .thenReturn(measures);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<MeasureListDTO>> response =
        controller.getMeasures(
            principal, List.of(OwnershipType.SHARED), 10, 0, "lastModifiedAt", "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(
            eq(null),
            eq(List.of(OwnershipType.SHARED)),
            eq(false),
            any(Pageable.class),
            eq("test.user"));

    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void getMeasuresWithAllOwnershipType() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));
    when(principal.getName()).thenReturn("test.user");
    when(measureService.getMeasuresByCriteria(
            eq(null),
            eq(List.of(OwnershipType.ALL)),
            eq(false),
            any(Pageable.class),
            eq("test.user")))
        .thenReturn(measures);
    ResponseEntity<Page<MeasureListDTO>> response =
        controller.getMeasures(
            principal, List.of(OwnershipType.ALL), 10, 0, "lastModifiedAt", "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(
            eq(null),
            eq(List.of(OwnershipType.ALL)),
            eq(false),
            any(Pageable.class),
            eq("test.user"));
    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void getDraftedMeasures() {
    // pass a list of measures to the GET Measures and return those that are draft status
    measure1.setId("testId");
    Map<String, Boolean> measures = new HashMap<>();
    measures.put("IDIDID", Boolean.TRUE);

    when(measureService.getMeasureDrafts(anyList())).thenReturn(measures);
    List<String> listOfMeasureIds = new ArrayList<>();
    listOfMeasureIds.add("testId");
    ResponseEntity<Map<String, Boolean>> response = controller.getDraftStatuses(listOfMeasureIds);
    verify(measureService, times(1)).getMeasureDrafts(anyList());

    verifyNoMoreInteractions(measureService);
    assertNotNull(response.getBody().get("IDIDID"));
  }

  @Test
  void getMeasuresByMeasureSetId() {
    measure1.setId("testId");
    MeasureSearchCriteria searchCriteria = MeasureSearchCriteria.builder().build();
    List<MeasureListDTO> measures = Arrays.asList(measureList);
    when(measureSetService.getMeasuresByMeasureSetId(
            anyString(), anyBoolean(), any(MeasureSearchCriteria.class)))
        .thenReturn(measures);
    ResponseEntity<List<MeasureListDTO>> response =
        controller.getMeasuresByMeasureSetId("test", false, searchCriteria);
    verify(measureSetService, times(1))
        .getMeasuresByMeasureSetId(anyString(), anyBoolean(), any(MeasureSearchCriteria.class));
    assertNotNull(response.getBody());
  }

  @Test
  void getMeasure() {
    when(principal.getName()).thenReturn("test.user");
    String id = "testid";
    Optional<Measure> optionalMeasure = Optional.of(measure1);
    doReturn(optionalMeasure).when(repository).findByIdAndActive(id, true);
    // measure found
    ResponseEntity<Measure> response = controller.getMeasure(id, principal);
    assertEquals(
        measure1.getMeasureName(), Objects.requireNonNull(response.getBody()).getMeasureName());

    // if measure not found
    Optional<Measure> empty = Optional.empty();
    doReturn(empty).when(repository).findByIdAndActive(id, true);
    response = controller.getMeasure(id, principal);
    assertNull(response.getBody());
    assertEquals(response.getStatusCode().value(), 404);
  }

  @Test
  void updateMeasureSuccessfully() {
    ArgumentCaptor<Measure> saveMeasureArgCaptor = ArgumentCaptor.forClass(Measure.class);
    when(principal.getName()).thenReturn("test.user2");

    Instant createdAt = Instant.now().minus(300, ChronoUnit.SECONDS);
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDescription("TestDescription");
    metaData.setCopyright("TestCopyright");
    metaData.setDisclaimer("TestDisclaimer");
    metaData.setRationale("TestRationale");
    metaData.setDraft(true);
    measure1.setMeasureMetaData(metaData);
    measure1.setMeasurementPeriodStart(new Date("12/02/2020"));
    measure1.setMeasurementPeriodEnd(new Date("12/02/2021"));
    Measure originalMeasure =
        measure1.toBuilder()
            .id("5399aba6e4b0ae375bfdca88")
            .createdAt(createdAt)
            .createdBy("test.user2")
            .build();

    Instant original = Instant.now().minus(140, ChronoUnit.HOURS);

    Measure m1 =
        originalMeasure.toBuilder()
            .createdBy("test.user")
            .createdAt(original)
            .measurementPeriodStart(new Date("12/02/2021"))
            .measurementPeriodEnd(new Date("12/02/2022"))
            .lastModifiedBy("test.user")
            .lastModifiedAt(original)
            .build();

    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenReturn(m1);
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            originalMeasure.toBuilder()
                .measureSet(MeasureSet.builder().owner("test.user").build())
                .build());

    ResponseEntity<Measure> response =
        controller.updateMeasure(m1.getId(), m1, principal, "Bearer TOKEN");
    assertThat(response.getBody(), is(notNullValue()));
    assertThat(response.getBody(), is(equalTo(m1)));
    assertEquals(m1, response.getBody());
    verify(measureService, times(1))
        .updateMeasure(
            any(Measure.class), anyString(), saveMeasureArgCaptor.capture(), anyString());
    assertThat(saveMeasureArgCaptor.getValue(), is(equalTo(m1)));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            targetClassArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            performedByArgumentCaptor.capture());
    assertNotNull(targetIdArgumentCaptor.getValue());
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.UPDATED)));
    assertThat(performedByArgumentCaptor.getValue(), is(equalTo("test.user2")));
  }

  @Test
  void createCmsId() {
    when(principal.getName()).thenReturn("test.user2");

    final MeasureSet measureSet =
        MeasureSet.builder()
            .id("f225481c-921e-4015-9e14-e5046bfac9ff")
            .cmsId(6)
            .measureSetId("measureSetId")
            .owner("test.com")
            .acls(null)
            .build();

    when(measureSetService.createAndUpdateCmsId(anyString(), anyString())).thenReturn(measureSet);
    ResponseEntity<MeasureSet> response = controller.createCmsId(measureSet.getId(), principal);

    assertThat(response.getBody(), is(notNullValue()));
    assertThat(response.getBody(), is(equalTo(measureSet)));
    assertEquals(measureSet, response.getBody());
    verify(measureSetService, times(1)).createAndUpdateCmsId(anyString(), anyString());
  }

  @Test
  void updateMeasureSuccessfullyLogDeleted() {
    ArgumentCaptor<Measure> saveMeasureArgCaptor = ArgumentCaptor.forClass(Measure.class);
    when(principal.getName()).thenReturn("test.user2");

    Instant createdAt = Instant.now().minus(300, ChronoUnit.SECONDS);
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDescription("TestDescription");
    metaData.setCopyright("TestCopyright");
    metaData.setDisclaimer("TestDisclaimer");
    metaData.setRationale("TestRationale");
    metaData.setDraft(true);
    measure1.setMeasureMetaData(metaData);
    measure1.setMeasurementPeriodStart(new Date("12/02/2020"));
    measure1.setMeasurementPeriodEnd(new Date("12/02/2021"));
    Measure originalMeasure =
        measure1.toBuilder()
            .id("5399aba6e4b0ae375bfdca88")
            .active(true)
            .createdAt(createdAt)
            .createdBy("test.user2")
            .build();

    Instant original = Instant.now().minus(140, ChronoUnit.HOURS);

    Measure m1 =
        originalMeasure.toBuilder()
            .createdBy("test.user")
            .createdAt(original)
            .measurementPeriodStart(new Date("12/02/2021"))
            .measurementPeriodEnd(new Date("12/02/2022"))
            .lastModifiedBy("test.user")
            .lastModifiedAt(original)
            .active(false)
            .build();

    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            originalMeasure.toBuilder()
                .measureSet(MeasureSet.builder().owner("test.user2").build())
                .build());
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));

    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenReturn(m1);

    ResponseEntity<Measure> response =
        controller.updateMeasure(m1.getId(), m1, principal, "Bearer TOKEN");

    assertEquals(m1, response.getBody());
    verify(measureService, times(1))
        .updateMeasure(
            any(Measure.class), anyString(), saveMeasureArgCaptor.capture(), anyString());
    assertThat(saveMeasureArgCaptor.getValue(), is(equalTo(m1)));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            targetClassArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            performedByArgumentCaptor.capture());
    assertNotNull(targetIdArgumentCaptor.getValue());
    assertThat(targetClassArgumentCaptor.getValue(), is(equalTo(Measure.class)));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.DELETED)));
    assertThat(performedByArgumentCaptor.getValue(), is(equalTo("test.user2")));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForNullId() {
    when(principal.getName()).thenReturn("test.user2");

    assertThrows(
        InvalidIdException.class,
        () -> controller.updateMeasure(null, measure1, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForInvalidCredentials() {
    when(principal.getName()).thenReturn("aninvalidUser@gmail.com");
    measure1.setCreatedBy("MSR01");
    measure1.setActive(true);
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            measure1.toBuilder().measureSet(MeasureSet.builder().owner("MSR01").build()).build());
    doThrow(new UnauthorizedException("Measure", measure1.getId(), "aninvalidUser@gmail.com"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));

    var testMeasure = new Measure();
    testMeasure.setActive(false);
    testMeasure.setCreatedBy("anotheruser");
    testMeasure.setId("testid");
    testMeasure.setMeasureName("MSR01");
    testMeasure.setVersion(new Version(0, 0, 1));

    assertThrows(
        UnauthorizedException.class,
        () -> controller.updateMeasure("testid", testMeasure, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForUpdatingSoftDeletedMeasure() {
    when(principal.getName()).thenReturn("validuser@gmail.com");
    measure1.setCreatedBy("validuser@gmail.com");
    measure1.setActive(false);
    measure1.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            measure1.toBuilder()
                .measureSet(MeasureSet.builder().owner("validuser@gmail.com").build())
                .build());
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));

    var testMeasure = new Measure();
    testMeasure.setActive(false);
    testMeasure.setCreatedBy("validuser@gmail.com");
    testMeasure.setId("testid");
    testMeasure.setMeasureName("MSR01");
    testMeasure.setVersion(new Version(0, 0, 1));

    assertThrows(
        UnauthorizedException.class,
        () -> controller.updateMeasure("testid", testMeasure, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForSoftDeletedMeasure() {
    when(principal.getName()).thenReturn("validUser@gmail.com");
    measure1.setCreatedBy("MSR01");
    measure1.setActive(false);
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            measure1.toBuilder().measureSet(MeasureSet.builder().owner("MSR01").build()).build());

    doThrow(new UnauthorizedException("Measure", measure1.getId(), "validUser@gmail.com"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));

    var testMeasure = new Measure();
    testMeasure.setActive(true);
    testMeasure.setCreatedBy("validUser@gmail.com");
    testMeasure.setId("testid");
    testMeasure.setMeasureName("MSR01");
    testMeasure.setVersion(new Version(0, 0, 1));

    assertThrows(
        UnauthorizedException.class,
        () -> controller.updateMeasure("testid", testMeasure, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsInvalidDeletionCredentialsException() {
    when(principal.getName()).thenReturn("sharedUser@gmail.com");
    measure1.setCreatedBy("MSR01");
    measure1.setActive(true);
    measure1.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());
    AclSpecification acl = new AclSpecification();
    acl.setUserId("sharedUser@gmail.com");
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            measure1.toBuilder()
                .measureSet(MeasureSet.builder().owner("MSR01").acls(List.of(acl)).build())
                .build());
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));

    var testMeasure = new Measure();
    testMeasure.setActive(false);
    testMeasure.setCreatedBy("anotheruser");
    testMeasure.setId("testid");
    testMeasure.setMeasureName("MSR01");
    testMeasure.setVersion(new Version(0, 0, 1));
    testMeasure.setActive(false);
    doThrow(new UnauthorizedException("Measure", measure1.getId(), "invalidUser@gmail.com"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class), isNull());
    assertThrows(
        UnauthorizedException.class,
        () -> controller.updateMeasure("testid", testMeasure, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsInvalidDraftStatusException() {
    when(principal.getName()).thenReturn("sharedUser@gmail.com");
    measure1.setCreatedBy("MSR01");
    measure1.setActive(true);
    measure1.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    AclSpecification acl = new AclSpecification();
    acl.setUserId("sharedUser@gmail.com");
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            measure1.toBuilder()
                .measureSet(MeasureSet.builder().owner("test.user").acls(List.of(acl)).build())
                .build());

    var testMeasure = new Measure();
    testMeasure.setActive(false);
    testMeasure.setCreatedBy("anotheruser");
    testMeasure.setId("testid");
    testMeasure.setMeasureName("MSR01");
    testMeasure.setVersion(new Version(0, 0, 1));
    testMeasure.setActive(false);
    assertThrows(
        InvalidDraftStatusException.class,
        () -> controller.updateMeasure("testid", testMeasure, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForEmptyStringId() {
    when(principal.getName()).thenReturn("test.user2");

    assertThrows(
        InvalidIdException.class,
        () -> controller.updateMeasure("", measure1, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForNonMatchingIds() {
    when(principal.getName()).thenReturn("test.user2");
    Measure m1234 = measure1.toBuilder().id("ID1234").build();

    assertThrows(
        InvalidIdException.class,
        () -> controller.updateMeasure("ID5678", m1234, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForLockedTestCasesWhenUpdatingGroups() {
    Measure m1234 =
        measure1.toBuilder()
            .id("ID1234")
            .createdBy("test.user2")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();
    when(principal.getName()).thenReturn("test.user2");
    when(measureService.findMeasureById(anyString())).thenReturn(m1234);
    when(testCaseLockService.isAnyTestCaseLockedByOthers(anyString(), anyString()))
        .thenReturn(true);

    Measure updatedMeasure =
        m1234.toBuilder().groups(List.of(Group.builder().id("group_1").build())).build();

    assertThrows(
        LockNotObtainedException.class,
        () -> controller.updateMeasure("ID1234", updatedMeasure, principal, "Bearer TOKEN"));
  }

  @Test
  void testTopLevelUpdateMeasureSuccessfulWhenTestCaseLocked() {
    Measure m1234 =
        measure1.toBuilder()
            .id("ID1234")
            .createdBy("test.user2")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .groups(List.of(Group.builder().id("group_1").build()))
            .build();
    when(principal.getName()).thenReturn("test.user2");
    when(measureService.findMeasureById(anyString())).thenReturn(m1234);
    when(testCaseLockService.isAnyTestCaseLockedByOthers(anyString(), anyString()))
        .thenReturn(true);

    Measure updatedMeasure = m1234.toBuilder().measureName("New Name").build();

    assertDoesNotThrow(
        () -> controller.updateMeasure("ID1234", updatedMeasure, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureSuccessfulWhenNoTestCasesLocked() {
    Measure m1234 =
        measure1.toBuilder()
            .id("ID1234")
            .createdBy("test.user2")
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .groups(List.of(Group.builder().id("group_1").build()))
            .build();
    when(principal.getName()).thenReturn("test.user2");
    when(measureService.findMeasureById(anyString())).thenReturn(m1234);
    when(testCaseLockService.isAnyTestCaseLockedByOthers(anyString(), anyString()))
        .thenReturn(false);

    Measure updatedMeasure = m1234.toBuilder().measureName("New Name").build();

    assertDoesNotThrow(
        () -> controller.updateMeasure("ID1234", updatedMeasure, principal, "Bearer TOKEN"));
  }

  @Test
  void updateNonExistingMeasure() {
    when(principal.getName()).thenReturn("test.user2");

    // no measure id specified
    assertThrows(
        InvalidIdException.class,
        () -> controller.updateMeasure(measure1.getId(), measure1, principal, "Bearer TOKEN"));
    // non-existing measure or measure with fake id
    measure1.setId("5399aba6e4b0ae375bfdca88");

    when(measureService.findMeasureById(anyString())).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> controller.updateMeasure(measure1.getId(), measure1, principal, "Bearer TOKEN"));
  }

  @Test
  void updateUnAuthorizedMeasure() {
    when(principal.getName()).thenReturn("unAuthorized user");
    measure1.setCreatedBy("actual owner");
    measure1.setActive(true);
    measure1.setMeasurementPeriodStart(new Date());
    measure1.setId("testid");
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            measure1.toBuilder()
                .measureSet(MeasureSet.builder().owner("test.user").build())
                .build());
    doThrow(new UnauthorizedException("Measure", "testid", "unAuthorized user"))
        .when(measureService)
        .verifyAuthorization(anyString(), any(Measure.class));

    var testMeasure = new Measure();
    testMeasure.setActive(true);
    testMeasure.setId("testid");
    assertThrows(
        UnauthorizedException.class,
        () -> controller.updateMeasure("testid", testMeasure, principal, "Bearer TOKEN"));
  }

  @Test
  void createGroup() {
    Group group =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .build();
    when(principal.getName()).thenReturn("test.user");

    doReturn(group)
        .when(groupService)
        .createOrUpdateGroup(any(Group.class), any(String.class), any(String.class));

    Group newGroup = new Group();

    ResponseEntity<Group> response = controller.createGroup(newGroup, "measure-id", principal);
    assertNotNull(response.getBody());
    assertEquals(group.getId(), response.getBody().getId());
    assertEquals(group.getScoring(), response.getBody().getScoring());
    assertEquals(group.getPopulations(), response.getBody().getPopulations());
  }

  @Test
  void deleteGroup() {
    when(principal.getName()).thenReturn("test.user");
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measure-id").build());

    Measure updatedMeasure =
        Measure.builder().id("measure-id").createdBy("test.user").groups(null).build();
    doReturn(updatedMeasure)
        .when(groupService)
        .deleteMeasureGroup(any(String.class), any(String.class), any(String.class));

    ResponseEntity<Measure> output =
        controller.deleteMeasureGroup("measure-id", "testgroupid", principal);

    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertNull(output.getBody().getGroups());
    assertNull(output.getBody().getMeasureLock());
  }

  @Test
  void deleteGroupWithMeasureLockedBySameUser() {
    when(principal.getName()).thenReturn("test.user");
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            Measure.builder()
                .id("measure-id")
                .measureLock(MeasureLock.builder().lockedBy("test.user").build())
                .build());

    Measure updatedMeasure =
        Measure.builder().id("measure-id").createdBy("test.user").groups(null).build();
    doReturn(updatedMeasure)
        .when(groupService)
        .deleteMeasureGroup(any(String.class), any(String.class), any(String.class));

    ResponseEntity<Measure> output =
        controller.deleteMeasureGroup("measure-id", "testgroupid", principal);

    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
  }

  @Test
  void deleteGroupWithMeasureLocked() {
    when(principal.getName()).thenReturn("test.user");

    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            Measure.builder()
                .id("measure-id")
                .measureLock(MeasureLock.builder().lockedBy("another.user").build())
                .build());
    assertThrows(
        LockNotObtainedException.class,
        () -> controller.deleteMeasureGroup("measure-id", "testgroupid", principal));
  }

  @Test
  void deleteGroupThrowsResourceNotFoundExceptionWhenMeasureNotFound() {
    when(principal.getName()).thenReturn("test.user");
    when(measureService.findMeasureById(anyString())).thenReturn(null);
    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> controller.deleteMeasureGroup("measure-id", "testgroupid", principal));
    assertEquals(
        "Unable to check lock for provided measure because measure was not found", ex.getMessage());
  }

  @Test
  void updateGroup() {
    Group group =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-2",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .build();
    when(principal.getName()).thenReturn("test.user");
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measure-id").build());

    doReturn(group)
        .when(groupService)
        .createOrUpdateGroup(any(Group.class), any(String.class), any(String.class));

    Group newGroup = new Group();

    ResponseEntity<Group> response = controller.updateGroup(newGroup, "measure-id", principal);
    assertNotNull(response.getBody());
    assertEquals(group.getId(), response.getBody().getId());
    assertEquals(group.getScoring(), response.getBody().getScoring());
    assertEquals(group.getPopulations(), response.getBody().getPopulations());
  }

  @Test
  void searchOwnedMeasuresByNameOrEcqmTitle() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));

    when(principal.getName()).thenReturn("test.user");

    doReturn(measures)
        .when(measureService)
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.OWNED)),
            eq(false),
            any(Pageable.class),
            eq("test.user"));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test criteria").build();
    ResponseEntity<Page<MeasureListDTO>> response =
        controller.measureSearchByCriteria(
            principal,
            List.of(OwnershipType.OWNED),
            false,
            measureSearchCriteria,
            10,
            0,
            "lastModifiedAt",
            "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.OWNED)),
            eq(false),
            any(Pageable.class),
            eq("test.user"));

    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void searchSharedMeasuresByNameOrEcqmTitle() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));

    when(principal.getName()).thenReturn("test.user");

    doReturn(measures)
        .when(measureService)
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.SHARED)),
            eq(false),
            any(Pageable.class),
            eq("test.user"));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test criteria").build();
    ResponseEntity<Page<MeasureListDTO>> response =
        controller.measureSearchByCriteria(
            principal,
            List.of(OwnershipType.SHARED),
            false,
            measureSearchCriteria,
            10,
            0,
            "lastModifiedAt",
            "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.SHARED)),
            eq(false),
            any(Pageable.class),
            eq("test.user"));

    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void searchAllMeasuresByNameOrEcqmTitle() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));

    when(principal.getName()).thenReturn("test.user");

    doReturn(measures)
        .when(measureService)
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.ALL)),
            eq(false),
            any(Pageable.class),
            eq("test.user"));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test criteria").build();
    ResponseEntity<Page<MeasureListDTO>> response =
        controller.measureSearchByCriteria(
            principal,
            List.of(OwnershipType.ALL),
            false,
            measureSearchCriteria,
            10,
            0,
            "lastModifiedAt",
            "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.ALL)),
            eq(false),
            any(Pageable.class),
            eq("test.user"));

    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void createStratification() {
    Stratification stratification =
        Stratification.builder()
            .cqlDefinition("Initial Population")
            .association(PopulationType.INITIAL_POPULATION)
            .associations(List.of(PopulationType.INITIAL_POPULATION, PopulationType.NUMERATOR))
            .build();
    when(principal.getName()).thenReturn("test.user");

    doReturn(stratification)
        .when(groupService)
        .createOrUpdateStratification(
            any(String.class), any(String.class), any(Stratification.class), any(String.class));

    ResponseEntity<Stratification> response =
        controller.createStratification(new Stratification(), "measure-id", "group-id", principal);
    assertNotNull(response.getBody());
    assertEquals(stratification.getCqlDefinition(), response.getBody().getCqlDefinition());
    assertEquals(stratification.getAssociation(), response.getBody().getAssociation());
    assertEquals(stratification.getAssociations(), response.getBody().getAssociations());
  }

  @Test
  void deleteStratification() {
    when(principal.getName()).thenReturn("test.user");
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measure-id").build());

    Measure updatedMeasure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .groups(List.of(Group.builder().stratifications(null).build()))
            .build();
    doReturn(updatedMeasure)
        .when(groupService)
        .deleteStratification(
            any(String.class), any(String.class), any(String.class), any(String.class));

    ResponseEntity<Measure> output =
        controller.deleteStratification("measure-id", "testgroupid", "stratifactionid", principal);

    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertNull(output.getBody().getGroups().get(0).getStratifications());
  }

  @Test
  void updateStratification() {
    Stratification stratification =
        Stratification.builder()
            .cqlDefinition("Initial Population")
            .association(PopulationType.INITIAL_POPULATION)
            .associations(List.of(PopulationType.INITIAL_POPULATION, PopulationType.NUMERATOR))
            .build();
    when(principal.getName()).thenReturn("test.user");
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measure-id").build());

    doReturn(stratification)
        .when(groupService)
        .createOrUpdateStratification(
            any(String.class), any(String.class), any(Stratification.class), any(String.class));

    ResponseEntity<Stratification> response =
        controller.updateStratification(new Stratification(), "measure-id", "group-id", principal);
    assertNotNull(response.getBody());
    assertEquals(stratification.getCqlDefinition(), response.getBody().getCqlDefinition());
    assertEquals(stratification.getAssociation(), response.getBody().getAssociation());
    assertEquals(stratification.getAssociations(), response.getBody().getAssociations());
  }

  @Test
  public void testValidateCmsAssociationSuccessfully() {
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").cmsId(12).owner("OWNER").build();
    when(principal.getName()).thenReturn("test.user");
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measure-id").build());

    when(measureService.associateCmsId(
            any(String.class), any(String.class), any(String.class), any(Boolean.class)))
        .thenReturn(qiCoreMeasureSet);

    ResponseEntity<MeasureSet> result =
        controller.associateCmsId(principal, "qiCoreMeasureId", "qdmMeasureId", false);
    assertThat(result.getStatusCode(), is(equalTo(HttpStatus.OK)));
  }

  @Test
  void testGetLibraryUsage() {
    String libraryName = "Helper";
    String owner = "john";
    LibraryUsage libraryUsage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    when(measureService.findLibraryUsage(anyString())).thenReturn(List.of(libraryUsage));
    ResponseEntity<List<LibraryUsage>> response = controller.getLibraryUsage(libraryName);
    List<LibraryUsage> usage = response.getBody();
    assertThat(usage.size(), is(equalTo(1)));
    assertThat(usage.get(0).getName(), is(equalTo(libraryName)));
    assertThat(usage.get(0).getOwner(), is(equalTo(owner)));
  }

  @Test
  void testGetCounts() {
    when(principal.getName()).thenReturn("test.user");

    when(measureService.countMeasuresByOwnership(true, "test.user", List.of(OwnershipType.OWNED)))
        .thenReturn(5);
    when(measureService.countMeasuresByOwnership(true, "test.user", List.of(OwnershipType.SHARED)))
        .thenReturn(3);
    when(measureService.countMeasuresByOwnership(true, "test.user", List.of(OwnershipType.ALL)))
        .thenReturn(10);

    when(measureService.countMeasuresByReview(true, "test.user", List.of(OwnershipType.OWNED)))
            .thenReturn(2);

    when(measureService.countMeasuresByReview(true, "test.user", List.of(OwnershipType.ALL)))
            .thenReturn(5);

    // when(measureService.countMyMeasures(anyString())).thenReturn(5);
    ResponseEntity<Map<String, Integer>> response = controller.getCounts(principal);

    Map<String, Integer> result = response.getBody();

    assertThat(result.get("ownedMeasures"), is(equalTo(5)));
    assertThat(result.get("sharedMeasures"), is(equalTo(3)));
    assertThat(result.get("allMeasures"), is(equalTo(10)));
    assertThat(result.get("ownedReviews"), is(equalTo(2)));
    assertThat(result.get("allReviews"), is(equalTo(5)));
  }

  @Test
  public void testClearingTestCaseGroupPopulationValuesWhenScoringIsChangedForQDMMeasures() {
    TestCaseGroupPopulation testCaseGroupPopulation =
        TestCaseGroupPopulation.builder().groupId("groupId1").scoring("Cohort").build();

    TestCase testCase =
        TestCase.builder().id("testId1").groupPopulations(List.of(testCaseGroupPopulation)).build();

    QdmMeasure original =
        QdmMeasure.builder()
            .cql("original cql here")
            .model(ModelType.QDM_5_6.getValue())
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .errors(List.of(MeasureErrorType.ERRORS_ELM_JSON))
            .id("testId")
            .createdBy("test.user")
            .scoring(MeasureScoring.COHORT.toString())
            .groups(null)
            .testCases(List.of(testCase))
            .patientBasis(false)
            .build();

    QdmMeasure updated =
        original.toBuilder()
            .cql("changed cql here")
            .scoring(MeasureScoring.PROPORTION.toString())
            .build();

    Measure expected =
        updated.toBuilder().error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES).build();

    ArgumentCaptor<TestCase> saveTestCaseCaptor = ArgumentCaptor.forClass(TestCase.class);
    when(principal.getName()).thenReturn("test.user");

    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenReturn(expected);
    when(measureService.findMeasureById(anyString()))
        .thenReturn(
            original.toBuilder()
                .measureSet(MeasureSet.builder().owner("test.user").build())
                .build());
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));

    Measure output =
        controller.updateMeasure(updated.getId(), updated, principal, "Bearer TOKEN").getBody();
    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(expected)));
    assertThat(output.getTestCases().get(0).getGroupPopulations(), is(equalTo(new ArrayList<>())));

    verify(testCaseService, times(1))
        .updateTestCase(saveTestCaseCaptor.capture(), anyString(), anyString(), anyString());
    TestCase persisted = saveTestCaseCaptor.getValue();
    assertThat(persisted, is(equalTo(expected.getTestCases().get(0))));
  }

  @Test
  void updateMeasureTestCaseConfigurationReturnsUpdatedMeasure() {
    when(principal.getName()).thenReturn("test.user");
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measureId").build());

    Measure updatedMeasure = Measure.builder().id("measureId").build();
    TestCaseConfiguration testCaseConfig = new TestCaseConfiguration();

    when(measureService.updateMeasureTestCaseConfiguration(
            "test.user", "measureId", testCaseConfig, "Bearer TOKEN"))
        .thenReturn(updatedMeasure);

    ResponseEntity<Measure> response =
        controller.updateMeasureTestCaseConfiguration(
            "measureId", testCaseConfig, principal, "Bearer TOKEN");

    assertNotNull(response.getBody());
    assertEquals(updatedMeasure, response.getBody());
    verify(measureService, times(1))
        .updateMeasureTestCaseConfiguration(
            "test.user", "measureId", testCaseConfig, "Bearer TOKEN");
    verify(actionLogService, times(1))
        .logAction("measureId", Measure.class, ActionType.UPDATED, "test.user");
  }

  @Test
  void updateMeasureTestCaseConfigurationThrowsUnauthorizedExceptionForInvalidUser() {
    when(principal.getName()).thenReturn("invalid.user");
    when(measureService.findMeasureById(anyString()))
        .thenReturn(Measure.builder().id("measureId").build());

    TestCaseConfiguration testCaseConfig = new TestCaseConfiguration();

    doThrow(new UnauthorizedException("Measure", "measureId", "invalid.user"))
        .when(measureService)
        .updateMeasureTestCaseConfiguration(
            "invalid.user", "measureId", testCaseConfig, "Bearer TOKEN");

    assertThrows(
        UnauthorizedException.class,
        () ->
            controller.updateMeasureTestCaseConfiguration(
                "measureId", testCaseConfig, principal, "Bearer TOKEN"));
  }

  @Test
  public void testTransferMeasures() {
    when(principal.getName()).thenReturn("test.user");
    when(measureService.findMeasureById(anyString())).thenReturn(measure1);
    when(measureService.transferMeasures(
            anyList(), anyString(), anyBoolean(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());
    ResponseEntity<List<String>> result =
        controller.transferMeasures(
            List.of("testMeasureId"), "testHarpId", true, principal, "testToken");

    assertEquals(HttpStatus.OK, result.getStatusCode());
  }

  @Test
  public void testTransferMeasuresPartialFailure() {
    when(principal.getName()).thenReturn("test.user");
    // successfully transferred measure
    Measure successfullMeasure = measure1.toBuilder().id("measureId1").build();
    // locked measure
    Measure lockedMeasure =
        measure1.toBuilder()
            .id("measureId2")
            .measureLock(MeasureLock.builder().lockedBy("another.user").build())
            .build();
    // Failed measure
    Measure failedMeasure = measure1.toBuilder().id("measureId3").build();
    // mock measure retrieval for all measures
    when(measureService.findMeasureById(successfullMeasure.getId())).thenReturn(successfullMeasure);
    when(measureService.findMeasureById(lockedMeasure.getId())).thenReturn(lockedMeasure);
    when(measureService.findMeasureById(failedMeasure.getId())).thenReturn(failedMeasure);

    // mock transfer result to indicate measureId3 failed due to some other reasons
    when(measureService.transferMeasures(
            anyList(), anyString(), anyBoolean(), anyString(), anyString()))
        .thenReturn(List.of(failedMeasure.getId()));

    ResponseEntity<List<String>> result =
        controller.transferMeasures(
            List.of("measureId1", "measureId2", "measureId3"),
            "harpId1",
            true,
            principal,
            "testToken");

    assertEquals(HttpStatus.MULTI_STATUS, result.getStatusCode());
    assertNotNull(result.getBody());
    assertEquals(2, result.getBody().size());
    assertTrue(result.getBody().contains(failedMeasure.getId()));
    assertTrue(result.getBody().contains(lockedMeasure.getId()));
  }

  @Test
  public void testTransferMeasuresEmptyMeasureIds() {
    when(principal.getName()).thenReturn("test.user");
    ResponseEntity<List<String>> result =
        controller.transferMeasures(
            Collections.emptyList(), "testHarpId", true, principal, "testToken");

    assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    assertNotNull(result.getBody());
    assertTrue(result.getBody().isEmpty());
  }

  @Test
  void getMeasureHistoryReturnsActionsForValidMeasureId() {
    when(principal.getName()).thenReturn("test.user");

    List<Action> actions =
        List.of(
            Action.builder().actionType(ActionType.CREATED).performedBy("test.user").build(),
            Action.builder().actionType(ActionType.UPDATED).performedBy("test.user").build());

    when(measureService.getMeasureHistory("measureId", "test.user")).thenReturn(actions);

    ResponseEntity<List<Action>> response = controller.getMeasureHistory("measureId", principal);

    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().size());
    assertEquals(ActionType.CREATED, response.getBody().get(0).getActionType());
    assertEquals(ActionType.UPDATED, response.getBody().get(1).getActionType());
  }

  @Test
  void getMeasureHistoryReturnsEmptyListForNonExistentMeasureId() {
    when(principal.getName()).thenReturn("test.user");

    when(measureService.getMeasureHistory("nonExistentId", "test.user")).thenReturn(List.of());

    ResponseEntity<List<Action>> response =
        controller.getMeasureHistory("nonExistentId", principal);

    assertNotNull(response.getBody());
    assertTrue(response.getBody().isEmpty());
  }

  @Test
  void getMeasureHistoryThrowsUnauthorizedExceptionForInvalidUser() {
    when(principal.getName()).thenReturn("invalid.user");

    doThrow(new UnauthorizedException("Measure", "measureId", "invalid.user"))
        .when(measureService)
        .getMeasureHistory("measureId", "invalid.user");

    assertThrows(
        UnauthorizedException.class, () -> controller.getMeasureHistory("measureId", principal));
  }

  @Test
  void getMeasureWithLock() {
    when(principal.getName()).thenReturn("test.user");
    String id = "testid";
    Optional<Measure> optionalMeasure = Optional.of(measure1);
    doReturn(optionalMeasure).when(repository).findByIdAndActive(id, true);
    when(measureService.getMeasureLock(anyString(), anyString()))
        .thenReturn(
            gov.cms.madie.models.measure.MeasureLock.builder()
                .id(id)
                .lockedBy("another.user")
                .build());
    // measure found
    ResponseEntity<Measure> response = controller.getMeasure(id, principal);
    assertEquals(
        measure1.getMeasureName(), Objects.requireNonNull(response.getBody()).getMeasureName());
    assertNotNull(Objects.requireNonNull(response.getBody()).getMeasureLock());
    assertEquals(
        "another.user", Objects.requireNonNull(response.getBody()).getMeasureLock().getLockedBy());
  }

  @Test
  void compareMeasuresReturnsCqlDiffResultForValidMeasureIds() {
    Measure oldMeasure =
        Measure.builder()
            .id("oldMeasureId")
            .cql("library OldLibrary { define: 'Old CQL' }")
            .cqlLibraryName("OldLibrary")
            .build();

    Measure newMeasure =
        Measure.builder()
            .id("newMeasureId")
            .cql("library NewLibrary { define: 'New CQL' }")
            .cqlLibraryName("NewLibrary")
            .build();

    List<CqlFileComparisonDTO> comparisons =
        List.of(
            CqlFileComparisonDTO.builder()
                .oldFileName("OldLibrary.cql")
                .newFileName("NewLibrary.cql")
                .oldText("library OldLibrary { define: 'Old CQL' }")
                .newText("library NewLibrary { define: 'New CQL' }")
                .build());

    when(measureService.findMeasureById("oldMeasureId")).thenReturn(oldMeasure);
    when(measureService.findMeasureById("newMeasureId")).thenReturn(newMeasure);
    when(cqlDifferentiatorService.compareLibraries(anyMap(), anyMap(), eq(true)))
        .thenReturn(comparisons);

    ResponseEntity<CqlDiffResultDTO> response =
        controller.compareMeasures("oldMeasureId", "newMeasureId", true);

    assertNotNull(response.getBody());
    assertEquals("oldMeasureId", response.getBody().getOldMeasureId());
    assertEquals("newMeasureId", response.getBody().getNewMeasureId());
    assertEquals(1, response.getBody().getComparisons().size());
    assertEquals("OldLibrary.cql", response.getBody().getComparisons().get(0).getOldFileName());
    assertEquals("NewLibrary.cql", response.getBody().getComparisons().get(0).getNewFileName());
    assertEquals(
        "library OldLibrary { define: 'Old CQL' }",
        response.getBody().getComparisons().get(0).getOldText());
    assertEquals(
        "library NewLibrary { define: 'New CQL' }",
        response.getBody().getComparisons().get(0).getNewText());
  }

  @Test
  void compareMeasuresThrowsResourceNotFoundExceptionForInvalidOldMeasureId() {
    when(measureService.findMeasureById("oldMeasureId")).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> controller.compareMeasures("oldMeasureId", "newMeasureId", true));
  }

  @Test
  void compareMeasuresThrowsResourceNotFoundExceptionForInvalidNewMeasureId() {
    Measure oldMeasure =
        Measure.builder()
            .id("oldMeasureId")
            .cql("library OldLibrary { define: 'Old CQL' }")
            .cqlLibraryName("OldLibrary")
            .build();

    when(measureService.findMeasureById("oldMeasureId")).thenReturn(oldMeasure);
    when(measureService.findMeasureById("newMeasureId")).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> controller.compareMeasures("oldMeasureId", "newMeasureId", true));
  }

  @Test
  void compareMeasuresReturnsEmptyComparisonsForMeasuresWithoutCql() {
    Measure oldMeasure =
        Measure.builder().id("oldMeasureId").cql(null).cqlLibraryName("OldLibrary").build();

    Measure newMeasure =
        Measure.builder().id("newMeasureId").cql(null).cqlLibraryName("NewLibrary").build();

    when(measureService.findMeasureById("oldMeasureId")).thenReturn(oldMeasure);
    when(measureService.findMeasureById("newMeasureId")).thenReturn(newMeasure);

    ResponseEntity<CqlDiffResultDTO> response =
        controller.compareMeasures("oldMeasureId", "newMeasureId", true);

    assertNotNull(response.getBody());
    assertEquals("oldMeasureId", response.getBody().getOldMeasureId());
    assertEquals("newMeasureId", response.getBody().getNewMeasureId());
    assertTrue(response.getBody().getComparisons().isEmpty());
  }

  @Test
  void associateCmsIdThrowsExceptionWhenQiCoreMeasureIdIsBlank() {
    when(principal.getName()).thenReturn("test.user");

    InvalidIdException exception =
        assertThrows(
            InvalidIdException.class,
            () -> controller.associateCmsId(principal, "", "validQdmMeasureId", false));

    assertThat(
        exception.getMessage(), is(equalTo("CMS ID could not be associated. Please try again.")));
    verifyNoInteractions(measureService);
  }

  @Test
  void associateCmsIdThrowsExceptionWhenQdmMeasureIdIsBlank() {
    when(principal.getName()).thenReturn("test.user");

    InvalidIdException exception =
        assertThrows(
            InvalidIdException.class,
            () -> controller.associateCmsId(principal, "validQiCoreMeasureId", "", false));

    assertThat(
        exception.getMessage(), is(equalTo("CMS ID could not be associated. Please try again.")));
    verifyNoInteractions(measureService);
  }

  @Test
  void testUpdateMeasureExistingQdmMeasurePatientBasisNotSameAsUpdatedMeasure() {
    when(principal.getName()).thenReturn("test.user");
    TestCaseGroupPopulation tcgp =
        TestCaseGroupPopulation.builder()
            .populationValues(List.of(TestCasePopulationValue.builder().build()))
            .build();
    TestCase testCase = TestCase.builder().groupPopulations(List.of(tcgp)).build();
    TestCase testCase2 = TestCase.builder().groupPopulations(new ArrayList<>()).build();
    Measure existingMeasure =
        QdmMeasure.builder()
            .id("measureId")
            .model(ModelType.QDM_5_6.getValue())
            .createdBy("test.user")
            .patientBasis(true)
            .testCases(List.of(testCase))
            .active(true)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();

    Measure updatedMeasure =
        QdmMeasure.builder()
            .id("measureId")
            .model(ModelType.QDM_5_6.getValue())
            .createdBy("test.user")
            .patientBasis(false)
            .testCases(List.of(testCase2))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();

    when(measureService.findMeasureById("measureId")).thenReturn(existingMeasure);
    doNothing().when(measureService).verifyAuthorization(anyString(), any(Measure.class));
    when(measureService.updateMeasure(
            any(Measure.class), anyString(), any(Measure.class), anyString()))
        .thenReturn(updatedMeasure);

    Measure result =
        controller.updateMeasure("measureId", updatedMeasure, principal, "Bearer TOKEN").getBody();

    assertNotNull(result);
    assertEquals("measureId", result.getId());
    assertFalse(((QdmMeasure) result).isPatientBasis());
    assertTrue(((QdmMeasure) result).getTestCases().get(0).getGroupPopulations().isEmpty());
  }

  @Test
  void getMeasuresByIdsReturnsEmptyListWhenInputIdsEmpty() {
    List<String> emptyIds = List.of();
    when(measureService.getMeasuresByIds(emptyIds)).thenReturn(List.of());

    ResponseEntity<List<MeasureListDTO>> response = controller.getMeasuresByIds(emptyIds);

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().isEmpty());

    verify(measureService, times(1)).getMeasuresByIds(emptyIds);
    verifyNoMoreInteractions(measureService);
  }

  @Test
  void getMeasuresByIdsReturnsListWhenInputIdsNonEmptyVersionIsClass() {
    List<String> ids = List.of("m1", "m2");
    MeasureListDTO dto1 =
        MeasureListDTO.builder()
            .id("m1")
            .measureName("Alpha")
            .version(new Version(1, 0, 0))
            .build();

    MeasureListDTO dto2 =
        MeasureListDTO.builder().id("m2").measureName("Beta").version(new Version(2, 0, 0)).build();

    when(measureService.getMeasuresByIds(ids)).thenReturn(List.of(dto1, dto2));
    ResponseEntity<List<MeasureListDTO>> response = controller.getMeasuresByIds(ids);

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().size());

    MeasureListDTO r1 = response.getBody().get(0);
    MeasureListDTO r2 = response.getBody().get(1);

    assertEquals("m1", r1.getId());
    assertEquals("Alpha", r1.getMeasureName());
    assertEquals(new Version(1, 0, 0), r1.getVersion());

    assertEquals("m2", r2.getId());
    assertEquals("Beta", r2.getMeasureName());
    assertEquals(new Version(2, 0, 0), r2.getVersion());

    verify(measureService, times(1)).getMeasuresByIds(ids);
    verifyNoMoreInteractions(measureService);
  }
}
