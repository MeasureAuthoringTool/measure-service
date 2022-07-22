package cms.gov.madie.measure.resources;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import cms.gov.madie.measure.exceptions.*;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.measure.*;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.MeasureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import cms.gov.madie.measure.repositories.MeasureRepository;

@ExtendWith(MockitoExtension.class)
class MeasureControllerTest {

  @Mock private MeasureRepository repository;
  @Mock private MeasureService measureService;
  @Mock private ActionLogService actionLogService;

  @InjectMocks private MeasureController controller;

  private Measure measure;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;
  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;
  @Captor private ArgumentCaptor<String> performedByArgumentCaptor;

  @BeforeEach
  public void setUp() {
    measure = new Measure();
    measure.setActive(true);
    measure.setMeasureSetId("IDIDID");
    measure.setMeasureName("MSR01");
    measure.setVersion("0.001");
  }

  @Test
  void saveMeasure() {
    ArgumentCaptor<Measure> saveMeasureArgCaptor = ArgumentCaptor.forClass(Measure.class);
    measure.setId("testId");
    doReturn(measure).when(repository).save(ArgumentMatchers.any());

    Measure measures = new Measure();
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Measure> response = controller.addMeasure(measures, principal);
    assertNotNull(response.getBody());
    assertEquals("IDIDID", response.getBody().getMeasureSetId());

    verify(repository, times(1)).save(saveMeasureArgCaptor.capture());
    Measure savedMeasure = saveMeasureArgCaptor.getValue();
    assertThat(savedMeasure.getCreatedBy(), is(equalTo("test.user")));
    assertThat(savedMeasure.getLastModifiedBy(), is(equalTo("test.user")));
    assertThat(savedMeasure.getCreatedAt(), is(notNullValue()));
    assertThat(savedMeasure.getLastModifiedAt(), is(notNullValue()));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            performedByArgumentCaptor.capture());
    assertNotNull(targetIdArgumentCaptor.getValue());
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.CREATED)));
    assertThat(performedByArgumentCaptor.getValue(), is(equalTo("test.user")));
  }

  @Test
  void getMeasuresWithoutCurrentUserFilter() {
    Page<Measure> measures = new PageImpl<>(List.of(measure));
    when(repository.findAllByActive(any(Boolean.class), any(Pageable.class))).thenReturn(measures);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<Measure>> response = controller.getMeasures(principal, false, 10, 0);
    verify(repository, times(1)).findAllByActive(any(Boolean.class), any(Pageable.class));
    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void getMeasuresWithCurrentUserFilter() {
    Page<Measure> measures = new PageImpl<>(List.of(measure));
    when(repository.findAllByCreatedByAndActive(
            anyString(), any(Boolean.class), any(Pageable.class)))
        .thenReturn(measures);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<Measure>> response = controller.getMeasures(principal, true, 10, 0);
    verify(repository, times(1))
        .findAllByCreatedByAndActive(eq("test.user"), any(Boolean.class), any(Pageable.class));
    verifyNoMoreInteractions(repository);
    assertNotNull(response.getBody().getContent());
    assertNotNull(response.getBody().getContent().get(0));
    assertEquals("IDIDID", response.getBody().getContent().get(0).getMeasureSetId());
  }

  @Test
  void getMeasure() {
    String id = "testid";
    Optional<Measure> optionalMeasure = Optional.of(measure);
    doReturn(optionalMeasure).when(repository).findByIdAndActive(id, true);
    // measure found
    ResponseEntity<Measure> response = controller.getMeasure(id);
    assertEquals(
        measure.getMeasureName(), Objects.requireNonNull(response.getBody()).getMeasureName());

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
    measure.setMeasureMetaData(metaData);
    measure.setMeasurementPeriodStart(new Date("12/02/2020"));
    measure.setMeasurementPeriodEnd(new Date("12/02/2021"));
    Measure originalMeasure =
        measure
            .toBuilder()
            .id("5399aba6e4b0ae375bfdca88")
            .createdAt(createdAt)
            .createdBy("test.user")
            .build();

    Instant original = Instant.now().minus(140, ChronoUnit.HOURS);

    Measure m1 =
        originalMeasure
            .toBuilder()
            .createdBy("test.user")
            .createdAt(original)
            .measurementPeriodStart(new Date("12/02/2021"))
            .measurementPeriodEnd(new Date("12/02/2022"))
            .lastModifiedBy("test.user")
            .lastModifiedAt(original)
            .build();

    doReturn(Optional.of(originalMeasure))
        .when(repository)
        .findById(ArgumentMatchers.eq(originalMeasure.getId()));

    doAnswer((args) -> args.getArgument(0))
        .when(repository)
        .save(ArgumentMatchers.any(Measure.class));

    ResponseEntity<String> response = controller.updateMeasure(m1.getId(), m1, principal);
    assertEquals("Measure updated successfully.", response.getBody());
    verify(repository, times(1)).save(saveMeasureArgCaptor.capture());
    Measure savedMeasure = saveMeasureArgCaptor.getValue();
    assertThat(savedMeasure.getCreatedAt(), is(notNullValue()));
    assertThat(savedMeasure.getCreatedBy(), is(equalTo("test.user")));
    assertThat(savedMeasure.getLastModifiedAt(), is(notNullValue()));
    assertThat(savedMeasure.getLastModifiedBy(), is(equalTo("test.user2")));
    assertThat(savedMeasure.getMeasurementPeriodStart(), is(equalTo(new Date("12/02/2021"))));
    assertThat(savedMeasure.getMeasurementPeriodEnd(), is(equalTo(new Date("12/02/2022"))));
    assertThat(savedMeasure.getMeasureMetaData().getDescription(), is(equalTo("TestDescription")));
    assertThat(savedMeasure.getMeasureMetaData().getCopyright(), is(equalTo("TestCopyright")));
    assertThat(savedMeasure.getMeasureMetaData().getDisclaimer(), is(equalTo("TestDisclaimer")));
    assertThat(savedMeasure.getMeasureMetaData().getRationale(), is(equalTo("TestRationale")));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            performedByArgumentCaptor.capture());
    assertNotNull(targetIdArgumentCaptor.getValue());
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.DELETED)));
    assertThat(performedByArgumentCaptor.getValue(), is(equalTo("test.user2")));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForNullId() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");

    assertThrows(
        InvalidIdException.class, () -> controller.updateMeasure(null, measure, principal));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForInvalidCredentials() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("aninvalidUser@gmail.com");
    measure.setCreatedBy("MSR01");
    measure.setActive(false);
    when(repository.findById(anyString())).thenReturn(Optional.of(measure));

    var testMeasure = new Measure();
    testMeasure.setActive(false);
    testMeasure.setCreatedBy("anotheruser");
    testMeasure.setId("testid");
    testMeasure.setMeasureName("MSR01");
    testMeasure.setVersion("0.001");
    doThrow(new InvalidDeletionCredentialsException("invalidUser@gmail.com"))
        .when(measureService)
        .checkDeletionCredentials(anyString(), anyString());
    assertThrows(
        InvalidDeletionCredentialsException.class,
        () -> controller.updateMeasure("testid", testMeasure, principal));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForEmptyStringId() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");

    assertThrows(InvalidIdException.class, () -> controller.updateMeasure("", measure, principal));
  }

  @Test
  void testUpdateMeasureReturnsExceptionForNonMatchingIds() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");
    Measure m1234 = measure.toBuilder().id("ID1234").build();

    assertThrows(
        InvalidIdException.class, () -> controller.updateMeasure("ID5678", m1234, principal));
  }

  @Test
  void updateNonExistingMeasure() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user2");

    // no measure id specified
    assertThrows(
        InvalidIdException.class,
        () -> controller.updateMeasure(measure.getId(), measure, principal));
    // non-existing measure or measure with fake id
    measure.setId("5399aba6e4b0ae375bfdca88");
    Optional<Measure> empty = Optional.empty();

    doReturn(empty).when(repository).findById(measure.getId());

    ResponseEntity<String> response = controller.updateMeasure(measure.getId(), measure, principal);
    assertEquals("Measure does not exist.", response.getBody());
  }

  @Test
  void updateUnAuthorizedMeasure() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("unAuthorizedUser@gmail.com");
    measure.setCreatedBy("actualOwner@gmail.com");
    measure.setActive(true);
    measure.setMeasurementPeriodStart(new Date());
    measure.setId("testid");
    when(repository.findById(anyString())).thenReturn(Optional.of(measure));

    var testMeasure = new Measure();
    testMeasure.setActive(true);
    testMeasure.setId("testid");

    doThrow(new UnauthorizedException("Measure", measure.getId(), "unAuthorizedUser@gmail.com"))
        .when(measureService)
        .verifyAuthorization(anyString(), any());
    assertThrows(
        UnauthorizedException.class,
        () -> controller.updateMeasure("testid", testMeasure, principal));
  }

  @Test
  void createGroup() {
    Group group =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-1", PopulationType.INITIAL_POPULATION, "Initial Population")))
            .build();
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(group)
        .when(measureService)
        .createOrUpdateGroup(any(Group.class), any(String.class), any(String.class));

    Group newGroup = new Group();

    ResponseEntity<Group> response = controller.createGroup(newGroup, "measure-id", principal);
    assertNotNull(response.getBody());
    assertEquals(group.getId(), response.getBody().getId());
    assertEquals(group.getScoring(), response.getBody().getScoring());
    assertEquals(group.getPopulations(), response.getBody().getPopulations());
  }

  @Test
  void updateGroup() {
    Group group =
        Group.builder()
            .scoring("Cohort")
            .populations(
                List.of(
                    new Population(
                        "id-2", PopulationType.INITIAL_POPULATION, "Initial Population")))
            .build();
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    doReturn(group)
        .when(measureService)
        .createOrUpdateGroup(any(Group.class), any(String.class), any(String.class));

    Group newGroup = new Group();

    ResponseEntity<Group> response = controller.updateGroup(newGroup, "measure-id", principal);
    assertNotNull(response.getBody());
    assertEquals(group.getId(), response.getBody().getId());
    assertEquals(group.getScoring(), response.getBody().getScoring());
    assertEquals(group.getPopulations(), response.getBody().getPopulations());
  }

  @Test
  void testBundleMeasureThrowsNotFoundException() {
    Principal principal = mock(Principal.class);
    when(repository.findById(anyString())).thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> controller.getMeasureBundle("MeasureID", principal, "Bearer TOKEN"));
  }

  @Test
  void testBundleMeasureThrowsAccessException() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final Measure measure = Measure.builder().createdBy("OtherUser").build();
    when(repository.findById(anyString())).thenReturn(Optional.of(measure));
    assertThrows(
        UnauthorizedException.class,
        () -> controller.getMeasureBundle("MeasureID", principal, "Bearer TOKEN"));
  }

  @Test
  void testBundleMeasureThrowsOperationException() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final String elmJson = "{\"text\": \"ELM JSON\"}";
    final Measure measure =
        Measure.builder()
            .createdBy("test.user")
            .groups(
                List.of(
                    Group.builder()
                        .groupDescription("Group1")
                        .scoring(MeasureScoring.RATIO.toString())
                        .build()))
            .elmJson(elmJson)
            .build();
    when(repository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureService.bundleMeasure(any(Measure.class), anyString()))
        .thenThrow(
            new BundleOperationException("Measure", "MeasureID", new RuntimeException("cause")));
    assertThrows(
        BundleOperationException.class,
        () -> controller.getMeasureBundle("MeasureID", principal, "Bearer TOKEN"));
  }

  @Test
  void testBundleMeasureThrowsElmTranslationException() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final String elmJson = "{\"text\": \"ELM JSON\"}";
    final Measure measure =
        Measure.builder()
            .createdBy("test.user")
            .groups(
                List.of(
                    Group.builder()
                        .groupDescription("Group1")
                        .scoring(MeasureScoring.RATIO.toString())
                        .build()))
            .elmJson(elmJson)
            .build();
    when(repository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureService.bundleMeasure(any(Measure.class), anyString()))
        .thenThrow(new CqlElmTranslationErrorException(measure.getMeasureName()));
    assertThrows(
        CqlElmTranslationErrorException.class,
        () -> controller.getMeasureBundle("MeasureID", principal, "Bearer TOKEN"));
  }

  @Test
  void testBundleMeasureReturnsBundleString() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    final String elmJson = "{\"text\": \"ELM JSON\"}";
    final String json = "{\"message\": \"GOOD JSON\"}";
    final Measure measure =
        Measure.builder()
            .createdBy("test.user")
            .groups(
                List.of(
                    Group.builder()
                        .groupDescription("Group1")
                        .scoring(MeasureScoring.RATIO.toString())
                        .build()))
            .elmJson(elmJson)
            .build();
    when(repository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureService.bundleMeasure(any(Measure.class), anyString())).thenReturn(json);
    ResponseEntity<String> output =
        controller.getMeasureBundle("MeasureID", principal, "Bearer TOKEN");
    assertThat(output, is(notNullValue()));
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertThat(output.getBody(), is(equalTo(json)));
  }
}
