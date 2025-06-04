package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.exceptions.InvalidDraftStatusException;
import cms.gov.madie.measure.exceptions.InvalidIdException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.GroupService;
import cms.gov.madie.measure.services.MeasureService;
import cms.gov.madie.measure.services.MeasureSetService;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.Version;
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
import org.springframework.mock.web.MockHttpServletRequest;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
  @InjectMocks private MeasureController controller;

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
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Measure> response = controller.addMeasure(measures, false, principal, "");
    assertNotNull(response.getBody());
    assertEquals("IDIDID", response.getBody().getMeasureSetId());

    Measure savedMeasure = response.getBody();
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measure1.getMeasureName())));
    assertThat(savedMeasure.getId(), is(equalTo(measure1.getId())));
  }

  @Test
  void getMeasuresWithoutCurrentUserFilter() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    when(measureService.getMeasuresByCriteria(
            eq(null), eq(false), any(Pageable.class), eq("test.user")))
        .thenReturn(measures);
    ResponseEntity<Page<MeasureListDTO>> response =
        controller.getMeasures(principal, false, 10, 0, "lastModifiedAt", "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(eq(null), eq(false), any(Pageable.class), eq("test.user"));
    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void getMeasuresWithCurrentUserFilter() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));
    when(measureService.getMeasuresByCriteria(
            eq(null), eq(true), any(Pageable.class), eq("test.user")))
        .thenReturn(measures);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<MeasureListDTO>> response =
        controller.getMeasures(principal, true, 10, 0, "lastModifiedAt", "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(eq(null), eq(true), any(Pageable.class), eq("test.user"));

    verifyNoMoreInteractions(repository);
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
    List<MeasureListDTO> measures = Arrays.asList(measureList);
    when(measureSetService.getMeasuresByMeasureSetId(anyString(), anyBoolean()))
        .thenReturn(measures);
    ResponseEntity<List<MeasureListDTO>> response =
        controller.getMeasuresByMeasureSetId("test", false);
    verify(measureSetService, times(1)).getMeasuresByMeasureSetId(anyString(), anyBoolean());
    assertNotNull(response.getBody());
  }

  @Test
  void getMeasure() {
    String id = "testid";
    Optional<Measure> optionalMeasure = Optional.of(measure1);
    doReturn(optionalMeasure).when(repository).findByIdAndActive(id, true);
    // measure found
    ResponseEntity<Measure> response = controller.getMeasure(id);
    assertEquals(
        measure1.getMeasureName(), Objects.requireNonNull(response.getBody()).getMeasureName());

    // if measure not found
    Optional<Measure> empty = Optional.empty();
    doReturn(empty).when(repository).findByIdAndActive(id, true);
    response = controller.getMeasure(id);
    assertNull(response.getBody());
    assertEquals(response.getStatusCodeValue(), 404);
  }

  @Test
  void updateMeasureSuccessfully() {
    ArgumentCaptor<Measure> saveMeasureArgCaptor = ArgumentCaptor.forClass(Measure.class);
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
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
  void deleteCmsId() {
    Principal principal = mock(Principal.class);
    MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();

    String measureId = "measureId";

    final MeasureSet measureSet =
        MeasureSet.builder()
            .id("f225481c-921e-4015-9e14-e5046bfac9ff")
            .cmsId(6)
            .measureSetId("measureSetId")
            .owner("owner")
            .acls(null)
            .build();

    String expectedBody =
        String.format(
            "CMS Id of %s was deleted successfully from measure set with measure set id of %s",
            measureSet.getCmsId(), measureSet.getMeasureSetId());

    when(measureSetService.deleteCmsId(anyString(), anyInt(), anyString()))
        .thenReturn(expectedBody);

    ResponseEntity<String> response =
        controller.deleteCmsId(
            mockHttpServletRequest, measureId, measureSet.getCmsId(), "apiKey", "owner", principal);

    assertThat(response.getBody(), is(notNullValue()));
    assertEquals(expectedBody, response.getBody());
    verify(measureSetService, times(1)).deleteCmsId(anyString(), anyInt(), anyString());
  }

  @Test
  void updateMeasureSuccessfullyLogDeleted() {
    ArgumentCaptor<Measure> saveMeasureArgCaptor = ArgumentCaptor.forClass(Measure.class);
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");

    assertThrows(
        InvalidIdException.class,
        () -> controller.updateMeasure(null, measure1, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForInvalidCredentials() {
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");

    assertThrows(
        InvalidIdException.class,
        () -> controller.updateMeasure("", measure1, principal, "Bearer TOKEN"));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForNonMatchingIds() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");
    Measure m1234 = measure1.toBuilder().id("ID1234").build();

    assertThrows(
        InvalidIdException.class,
        () -> controller.updateMeasure("ID5678", m1234, principal, "Bearer TOKEN"));
  }

  @Test
  void updateNonExistingMeasure() {
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    Measure updatedMeasure =
        Measure.builder().id("measure-id").createdBy("test.user").groups(null).build();
    doReturn(updatedMeasure)
        .when(groupService)
        .deleteMeasureGroup(any(String.class), any(String.class), any(String.class));

    ResponseEntity<Measure> output =
        controller.deleteMeasureGroup("measure-id", "testgroupid", principal);

    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertNull(output.getBody().getGroups());
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
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

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
  void searchAllMeasuresByNameOrEcqmTitle() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(measures)
        .when(measureService)
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class), eq(false), any(Pageable.class), eq("test.user"));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test criteria").build();
    ResponseEntity<Page<MeasureListDTO>> response =
        controller.measureSearchByCriteria(
            principal, false, measureSearchCriteria, 10, 0, "lastModifiedAt", "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class), eq(false), any(Pageable.class), eq("test.user"));

    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void searchCurrentUsersMeasuresByNameOrEcqmTitle() {
    Page<MeasureListDTO> measures = new PageImpl<>(List.of(measureList));

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(measures)
        .when(measureService)
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class), eq(true), any(Pageable.class), eq("test.user"));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test criteria").build();
    ResponseEntity<Page<MeasureListDTO>> response =
        controller.measureSearchByCriteria(
            principal, true, measureSearchCriteria, 10, 0, "lastModifiedAt", "DESC");
    verify(measureService, times(1))
        .getMeasuresByCriteria(
            any(MeasureSearchCriteria.class), eq(true), any(Pageable.class), eq("test.user"));

    verifyNoMoreInteractions(repository);
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
    Principal principal = mock(Principal.class);
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
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

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
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

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
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

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
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    when(measureService.countAllMeasures()).thenReturn(500);
    when(measureService.countMyMeasures(anyString())).thenReturn(5);
    ResponseEntity<Map<String, Integer>> response = controller.getCounts(principal);
    Map<String, Integer> result = response.getBody();
    assertThat(result.get("allMeasures"), is(equalTo(500)));
    assertThat(result.get("myMeasures"), is(equalTo(5)));
  }
}
