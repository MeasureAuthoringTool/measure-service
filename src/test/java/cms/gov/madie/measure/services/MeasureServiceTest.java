package cms.gov.madie.measure.services;

import static java.util.Collections.emptySet;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.dto.*;
import cms.gov.madie.measure.exceptions.*;
import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.repositories.TestCasePatchRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.measure.*;
import gov.cms.mat.cql.CqlTextParser;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import cms.gov.madie.measure.utils.MeasureUtil;
import cms.gov.madie.measure.utils.ResourceUtil;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;

@ExtendWith(MockitoExtension.class)
public class MeasureServiceTest implements ResourceUtil {
  @Mock private MeasureRepository measureRepository;
  @Mock private MeasureSetRepository measureSetRepository;
  @Mock private TestCasePatchRepository testCasePatchRepository;
  @Mock private ElmTranslatorClient elmTranslatorClient;
  @Mock private MeasureUtil measureUtil;
  @Mock private ActionLogService actionLogService;
  @Mock private MeasureSetService measureSetService;
  @Mock private CqlTemplateConfigService cqlTemplateConfigService;
  @Mock private TerminologyValidationService terminologyValidationService;
  @Mock private MeasureLockService measureLockService;
  @Mock private UserServiceClient userServiceClient;
  @Mock private CompositeRelationshipService compositeRelationshipService;

  @Spy @InjectMocks private MeasureService measureService;
  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;

  private static final String ACCESS_TOKEN = "test-token";
  private Group group2;
  private MeasureMetaData draftMeasureMetaData;
  private MeasureMetaData finalMeasureMetaData;
  private String elmJson;
  private Measure measure1;
  private Measure measure2;
  private MeasureListDTO measureList;
  private List<Organization> organizationList;

  @BeforeEach
  public void setUp() {
    Stratification strat1 = new Stratification();
    strat1.setId("strat-1");
    strat1.setCqlDefinition("Initial Population");
    Stratification strat2 = new Stratification();
    strat2.setCqlDefinition("denominator_define");

    Stratification emptyStrat = new Stratification();
    // new group, not in DB, so no ID

    List<Reference> references =
        List.of(
            Reference.builder()
                .id("test reference id")
                .referenceText("test reference text")
                .referenceType("DOCUMENT")
                .build());
    List<Endorsement> endorsements =
        List.of(
            Endorsement.builder()
                .endorserSystemId("test endorsement system id")
                .endorser("NQF")
                .endorsementId("testEndorsementId")
                .build());
    List<MeasureDefinition> definitions =
        List.of(
            MeasureDefinition.builder()
                .id("test definition id")
                .term("test term")
                .definition("test definition")
                .build());

    List<Organization> developersList = new ArrayList<>();
    developersList.add(Organization.builder().name("SB 2").build());
    developersList.add(Organization.builder().name("SB 3").build());

    draftMeasureMetaData =
        MeasureMetaData.builder()
            .steward(Organization.builder().name("SB").build())
            .developers(developersList)
            .copyright("Copyright@SB")
            .references(references)
            .draft(true)
            .endorsements(endorsements)
            .definition("test definition")
            .experimental(false)
            .transmissionFormat("test transmission format")
            .measureDefinitions(definitions)
            .build();

    finalMeasureMetaData =
        MeasureMetaData.builder()
            .steward(Organization.builder().name("SB").build())
            .developers(developersList)
            .copyright("Copyright@SB")
            .references(references)
            .draft(false)
            .endorsements(endorsements)
            .definition("test definition")
            .experimental(false)
            .transmissionFormat("test transmission format")
            .build();

    // Present in DB and has ID
    group2 =
        Group.builder()
            .id("xyz-p12r-12ert")
            .populationBasis("Encounter")
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "FactorialOfFive",
                        null,
                        null,
                        "IntialPopulation_1")))
            .stratifications(List.of(strat1, emptyStrat))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();

    List<Group> groups = new ArrayList<>();
    groups.add(group2);
    elmJson = getData("/test_elm.json");
    measure1 =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .model(ModelType.QI_CORE.getValue())
            .cql("test cql")
            .elmJson(elmJson)
            .measureSetId("IDIDID")
            .cqlLibraryName("MSR01Library")
            .measureName("MSR01")
            .measureMetaData(draftMeasureMetaData)
            .version(new Version(0, 0, 1))
            .groups(groups)
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .build();

    measure2 =
        Measure.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .cql("test cql")
            .model(ModelType.QDM_5_6.getValue())
            .elmJson(elmJson)
            .measureSetId("2D2D2D")
            .measureName("MSR02")
            .version(new Version(0, 0, 1))
            .groups(groups)
            .createdAt(Instant.now())
            .createdBy("test user")
            .lastModifiedAt(Instant.now())
            .lastModifiedBy("test user")
            .measureMetaData(draftMeasureMetaData)
            .build();

    measureList =
        MeasureListDTO.builder()
            .active(true)
            .id("xyz-p13r-13ert")
            .model(ModelType.QI_CORE.getValue())
            .measureSetId("IDIDID")
            .measureName("MSR01")
            .measureMetaData(draftMeasureMetaData)
            .version(new Version(0, 0, 1))
            .build();

    organizationList = new ArrayList<>();
    organizationList.add(Organization.builder().name("SB").url("SB Url").build());
    organizationList.add(Organization.builder().name("SB 2").url("SB 2 Url").build());
    organizationList.add(Organization.builder().name("CancerLinQ").url("CancerLinQ Url").build());
    organizationList.add(Organization.builder().name("Innovaccer").url("Innovaccer Url").build());
  }

  @Test
  public void testVerifyAuthorizationByMeasureSetIdThrowsExceptionForMissingMeasureSet() {
    assertThrows(
        InvalidMeasureStateException.class,
        () -> measureService.verifyAuthorizationByMeasureSetId("THEUSER", "MS123", true));
  }

  @Test
  public void testVerifyAuthorizationByMeasureSetIdThrowsExceptionForEmptyAclsAndNonOwner() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    assertThrows(
        UnauthorizedException.class,
        () -> measureService.verifyAuthorizationByMeasureSetId("THEUSER", "MS123", true));
  }

  @Test
  public void testVerifyAuthorizationByMeasureSetIdDoesNothingForEmptyAclsAndOwner() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    measureService.verifyAuthorizationByMeasureSetId("OWNER", "MS123", true);
    verify(measureSetService, times(1)).findByMeasureSetId(eq("MS123"));
  }

  @Test
  public void testVerifyAuthorizationByMeasureSetIdDoesNothingForAclsAndSharedWith() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));
    acl1.setUserId("THEUSER");
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").acls(List.of(acl1)).build();
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    measureService.verifyAuthorizationByMeasureSetId("THEUSER", "MS123", false);
    verify(measureSetService, times(1)).findByMeasureSetId(eq("MS123"));
  }

  @Test
  public void testVerifyAuthorizationByMeasureSetIdDoesNothingForAclsAndSharedWithButOwnerOnly() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));
    acl1.setUserId("THEUSER");
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").acls(List.of(acl1)).build();
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    assertThrows(
        UnauthorizedException.class,
        () -> measureService.verifyAuthorizationByMeasureSetId("THEUSER", "MS123", true));
  }

  @Test
  public void testVerifyAuthorizationThrowsExceptionForMissingMeasureSet() {
    assertThrows(
        InvalidMeasureStateException.class,
        () -> measureService.verifyAuthorization("THEUSER", new Measure()));
  }

  @Test
  public void testVerifyAuthorizationThrowsExceptionForEmptyAclsAndNonOwner() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    Measure measure = Measure.builder().measureSet(measureSet).build();
    assertThrows(
        UnauthorizedException.class, () -> measureService.verifyAuthorization("THEUSER", measure));
  }

  @Test
  public void testVerifyAuthorizationThrowsExceptionForNotInAclsAndNonOwner() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("User1");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("User2");
    acl2.setRoles(Set.of(RoleEnum.SHARED_WITH));
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").acls(List.of(acl1, acl2)).build();
    Measure measure = Measure.builder().measureSet(measureSet).build();
    assertThrows(
        UnauthorizedException.class, () -> measureService.verifyAuthorization("THEUSER", measure));
  }

  @Test
  public void testVerifyAuthorizationDoesNothingForNotInAclsAndOwner() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("User1");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("User2");
    acl2.setRoles(Set.of(RoleEnum.SHARED_WITH));
    MeasureSet measureSet = MeasureSet.builder().owner("THEUSER").acls(List.of(acl1, acl2)).build();
    Measure measure = Measure.builder().measureSetId("MsID").build();
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    measureService.verifyAuthorization("THEUSER", measure);
    verify(measureSetService, times(1)).findByMeasureSetId(anyString());
  }

  @Test
  public void testVerifyAuthorizationDoesNothingForInAclsAndNonOwner() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("User1");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("THEUSER");
    acl2.setRoles(Set.of(RoleEnum.SHARED_WITH));
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").acls(List.of(acl1, acl2)).build();
    Measure measure = Measure.builder().measureSetId("MsID").build();
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    measureService.verifyAuthorization("THEUSER", measure);
    verify(measureSetService, times(1)).findByMeasureSetId(anyString());
  }

  @Test
  public void testVerifyAuthorizationByRoleDoesNothingForOwnerEmptyAcls() {
    // given
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").acls(null).build();
    Measure measure = Measure.builder().measureSetId("MsID").build();
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    // when
    measureService.verifyAuthorization("OWNER", measure, null);

    // then
    verify(measureSetService, times(1)).findByMeasureSetId(anyString());
  }

  @Test
  public void testFindMeasureByIdReturnsNullForEmptyOptional() {
    when(measureRepository.findById(isNull())).thenReturn(Optional.empty());
    Measure output = measureService.findMeasureById(null);
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testFindMeasureByIdIncludesMeasureSet() {
    MeasureSet measureSet = MeasureSet.builder().build();
    Measure measure = Measure.builder().measureSetId("MsetID").build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    Measure output = measureService.findMeasureById("MID");
    assertThat(output, is(notNullValue()));
    assertThat(output.getMeasureSet(), is(equalTo(measureSet)));
  }

  @Test
  public void testFindMeasureByIdIncludesMeasureLockWhenLockExists() {
    MeasureSet measureSet = MeasureSet.builder().build();
    Measure measure = Measure.builder().id("MID").measureSetId("MsetID").build();
    MeasureLock lock = MeasureLock.builder().id("lock-id").lockedBy("test.user").build();
    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);
    when(measureLockService.findByMeasureId(anyString())).thenReturn(lock);
    Measure output = measureService.findMeasureById("MID");
    assertThat(output, is(notNullValue()));
    assertThat(output.getMeasureLock(), is(notNullValue()));
    assertThat(output.getMeasureLock().getId(), is(equalTo("lock-id")));
    assertThat(output.getMeasureLock().getLockedBy(), is(equalTo("test.user")));
  }

  @Test
  public void testGetOwnedMeasuresByCriteria() {
    PageRequest initialPage = PageRequest.of(0, 10);

    Page<Measure> activeMeasures = new PageImpl<>(List.of(measure1));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test criteria").build();
    doReturn(activeMeasures)
        .when(measureRepository)
        .searchMeasuresByCriteria(
            eq("test.user"),
            any(PageRequest.class),
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.OWNED)));
    Object measures =
        measureService.getMeasuresByCriteria(
            measureSearchCriteria, List.of(OwnershipType.OWNED), initialPage, "test.user");
    assertNotNull(measures);
  }

  @Test
  public void testGetSharedMeasuresByCriteria() {
    PageRequest initialPage = PageRequest.of(0, 10);

    Page<Measure> activeMeasures = new PageImpl<>(List.of(measure1));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test criteria").build();
    doReturn(activeMeasures)
        .when(measureRepository)
        .searchMeasuresByCriteria(
            eq("test.user"),
            any(PageRequest.class),
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.SHARED)));
    Object measures =
        measureService.getMeasuresByCriteria(
            measureSearchCriteria, List.of(OwnershipType.SHARED), initialPage, "test.user");
    assertNotNull(measures);
  }

  @Test
  public void testGetAllMeasuresByCriteria() {
    PageRequest initialPage = PageRequest.of(0, 10);

    Page<Measure> activeMeasures = new PageImpl<>(List.of(measure1));

    MeasureSearchCriteria measureSearchCriteria =
        MeasureSearchCriteria.builder().searchField("test criteria").build();
    doReturn(activeMeasures)
        .when(measureRepository)
        .searchMeasuresByCriteria(
            eq("test.user"),
            any(PageRequest.class),
            any(MeasureSearchCriteria.class),
            eq(List.of(OwnershipType.ALL)));
    Object measures =
        measureService.getMeasuresByCriteria(
            measureSearchCriteria, List.of(OwnershipType.ALL), initialPage, "test.user");
    assertNotNull(measures);
  }

  @Test
  public void testGetMeasureDrafts() {
    measure2.getMeasureMetaData().setDraft(false);
    List<Measure> activeMeasures = List.of(measure1);
    List<String> measureSetIds = List.of("IDIDID", "2D2D2D");

    doReturn(activeMeasures)
        .when(measureRepository)
        .findAllByMeasureSetIdInAndActiveAndMeasureMetaDataDraft(anyList(), eq(true), eq(true));
    Map<String, Boolean> measures = measureService.getMeasureDrafts(measureSetIds);
    assertNotNull(measures);
    assertEquals(2, measures.size());
    assertFalse(measures.get("IDIDID"));
    assertTrue(measures.get("2D2D2D"));
  }

  @Test
  public void testCreateMeasureSuccessfullyWithDefaultCqlQDM() throws Exception {
    String cqlTemplate =
        IOUtils.toString(this.getClass().getResourceAsStream("/QDM56_CQLTemplate.txt"), "UTF-8");
    String usr = "john rao";
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .measureSetId("msid-1")
            .cqlLibraryName("VTE")
            .cql("")
            .elmJson(null)
            .measureMetaData(null)
            .cql(cqlTemplate)
            .createdBy(usr)
            .model(ModelType.QDM_5_6.getValue())
            .build();
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);

    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);
    when(cqlTemplateConfigService.getQdm56CqlTemplate()).thenReturn(cqlTemplate);

    Measure savedMeasure = measureService.createMeasure(measureToSave, usr, "token", true);
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measureToSave.getMeasureName())));
    assertThat(savedMeasure.getCqlLibraryName(), is(equalTo(measureToSave.getCqlLibraryName())));
    assertThat(savedMeasure.getCreatedBy(), is(equalTo(usr)));
    assertThat(savedMeasure.isCqlErrors(), is(equalTo(false)));
    assertThat(savedMeasure.getErrors(), is(emptySet()));
    assertThat(savedMeasure.getCql(), is(equalTo(cqlTemplate)));
  }

  @Test
  public void testCreateMeasureSuccessfullyWithNoCqlQDM() throws Exception {
    String usr = "john rao";
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .measureSetId("msid-1")
            .cqlLibraryName("VTE")
            .cql("")
            .elmJson(null)
            .measureMetaData(null)
            .createdBy(usr)
            .model(ModelType.QDM_5_6.getValue())
            .build();
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());

    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);
    when(cqlTemplateConfigService.getQdm56CqlTemplate()).thenReturn(null);

    Measure savedMeasure = measureService.createMeasure(measureToSave, usr, "token", true);
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measureToSave.getMeasureName())));
    assertThat(savedMeasure.getCqlLibraryName(), is(equalTo(measureToSave.getCqlLibraryName())));
    assertThat(savedMeasure.getCreatedBy(), is(equalTo(usr)));
    assertThat(savedMeasure.isCqlErrors(), is(equalTo(false)));
    assertThat(savedMeasure.getErrors(), is(emptySet()));
    assertThat(savedMeasure.getCql(), is(equalTo("")));
  }

  @Test
  public void testCreateMeasureSuccessfullyWithDefaultCqlQICore() throws Exception {
    String cqlTemplate =
        IOUtils.toString(
            this.getClass().getResourceAsStream("/QICore411_CQLTemplate.txt"), "UTF-8");
    String usr = "john rao";
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .measureSetId("msid-1")
            .cqlLibraryName("VTE")
            .cql("")
            .elmJson(null)
            .measureMetaData(null)
            .cql(cqlTemplate)
            .createdBy(usr)
            .model(ModelType.QI_CORE.getValue())
            .build();
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);

    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);
    when(cqlTemplateConfigService.getQiCore411CqlTemplate()).thenReturn(cqlTemplate);

    Measure savedMeasure = measureService.createMeasure(measureToSave, usr, "token", true);
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measureToSave.getMeasureName())));
    assertThat(savedMeasure.getCqlLibraryName(), is(equalTo(measureToSave.getCqlLibraryName())));
    assertThat(savedMeasure.getCreatedBy(), is(equalTo(usr)));
    assertThat(savedMeasure.isCqlErrors(), is(equalTo(false)));
    assertThat(savedMeasure.getErrors(), is(emptySet()));
    assertThat(savedMeasure.getCql(), is(equalTo(cqlTemplate)));
  }

  @Test
  public void testCreateMeasureSuccessfullyWithNoCqlQICore() throws Exception {
    String usr = "john rao";
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .measureSetId("msid-1")
            .cqlLibraryName("VTE")
            .cql("")
            .elmJson(null)
            .measureMetaData(null)
            .createdBy(usr)
            .model(ModelType.QI_CORE.getValue())
            .build();
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());

    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);
    when(cqlTemplateConfigService.getQiCore411CqlTemplate()).thenReturn(null);

    Measure savedMeasure = measureService.createMeasure(measureToSave, usr, "token", true);
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measureToSave.getMeasureName())));
    assertThat(savedMeasure.getCqlLibraryName(), is(equalTo(measureToSave.getCqlLibraryName())));
    assertThat(savedMeasure.getCreatedBy(), is(equalTo(usr)));
    assertThat(savedMeasure.isCqlErrors(), is(equalTo(false)));
    assertThat(savedMeasure.getErrors(), is(emptySet()));
    assertThat(savedMeasure.getCql(), is(equalTo("")));
  }

  @Test
  public void testCreateMeasureSuccessfullyWithDefaultCqlQICore600() throws Exception {
    String cqlTemplate =
        IOUtils.toString(
            this.getClass().getResourceAsStream("/QICore600_CQLTemplate.txt"), "UTF-8");
    String usr = "john rao";
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .measureSetId("msid-1")
            .cqlLibraryName("VTE")
            .cql("")
            .elmJson(null)
            .measureMetaData(null)
            .cql(cqlTemplate)
            .createdBy(usr)
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .build();
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);

    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);
    when(cqlTemplateConfigService.getQiCore600CqlTemplate()).thenReturn(cqlTemplate);

    Measure savedMeasure = measureService.createMeasure(measureToSave, usr, "token", true);
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measureToSave.getMeasureName())));
    assertThat(savedMeasure.getCqlLibraryName(), is(equalTo(measureToSave.getCqlLibraryName())));
    assertThat(savedMeasure.getCreatedBy(), is(equalTo(usr)));
    assertThat(savedMeasure.isCqlErrors(), is(equalTo(false)));
    assertThat(savedMeasure.getErrors(), is(emptySet()));
    assertThat(savedMeasure.getCql(), is(equalTo(cqlTemplate)));
  }

  @Test
  public void testCreateMeasureSuccessfullyWithNoCqlQICore600() throws Exception {
    String usr = "john rao";
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .measureSetId("msid-1")
            .cqlLibraryName("VTE")
            .cql("")
            .elmJson(null)
            .measureMetaData(null)
            .createdBy(usr)
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .build();
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());

    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);
    when(cqlTemplateConfigService.getQiCore600CqlTemplate()).thenReturn(null);

    Measure savedMeasure = measureService.createMeasure(measureToSave, usr, "token", true);
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measureToSave.getMeasureName())));
    assertThat(savedMeasure.getCqlLibraryName(), is(equalTo(measureToSave.getCqlLibraryName())));
    assertThat(savedMeasure.getCreatedBy(), is(equalTo(usr)));
    assertThat(savedMeasure.isCqlErrors(), is(equalTo(false)));
    assertThat(savedMeasure.getErrors(), is(emptySet()));
    assertThat(savedMeasure.getCql(), is(equalTo("")));
  }

  @Test
  public void testCreateMeasureSuccessfullyWithValidCql() {
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .measureSetId("msid-1")
            .cqlLibraryName("VTE")
            .build();

    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json(elmJson).build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);
    doNothing().when(terminologyValidationService).validateTerminology(anyString(), anyString());
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);

    Measure savedMeasure = measureService.createMeasure(measureToSave, "john rao", "token", false);
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measureToSave.getMeasureName())));
    assertThat(savedMeasure.getCqlLibraryName(), is(equalTo(measureToSave.getCqlLibraryName())));
    assertThat(savedMeasure.getErrors(), is(emptySet()));
    assertThat(savedMeasure.isCqlErrors(), is(equalTo(false)));
  }

  @Test
  public void testCreateMeasureSuccessfullyWithInvalidCqlAndTerminology() {
    String usr = "john rao";
    Set<MeasureErrorType> errors =
        Set.of(MeasureErrorType.ERRORS_ELM_JSON, MeasureErrorType.INVALID_TERMINOLOGY);
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .cqlLibraryName("VTE")
            .measureSetId("msid-1")
            .cqlErrors(true)
            .errors(errors)
            .createdBy(usr)
            .build();

    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json(elmJson).build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(true);
    doThrow(InvalidTerminologyException.class)
        .when(terminologyValidationService)
        .validateTerminology(anyString(), anyString());
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);

    Measure savedMeasure = measureService.createMeasure(measureToSave, usr, "token", false);
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measureToSave.getMeasureName())));
    assertThat(savedMeasure.getCqlLibraryName(), is(equalTo(measureToSave.getCqlLibraryName())));
    assertThat(savedMeasure.getCreatedBy(), is(equalTo(usr)));
    assertThat(savedMeasure.getErrors().size(), is(equalTo(2)));
    assertThat(savedMeasure.getErrors().contains(MeasureErrorType.ERRORS_ELM_JSON), is(true));
    assertThat(savedMeasure.getErrors().contains(MeasureErrorType.INVALID_TERMINOLOGY), is(true));
    assertThat(savedMeasure.isCqlErrors(), is(equalTo(true)));
  }

  @Test
  public void testCreateMeasureWhenLibraryNameDuplicate() {
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .active(true)
            .cqlLibraryName("VTE")
            .cql("")
            .elmJson(null)
            .build();
    List<Measure> measureList = Collections.singletonList(measure1);
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(measureList);

    assertThrows(
        DuplicateKeyException.class,
        () -> measureService.createMeasure(measureToSave, "john rao", "token", false),
        "CQL library with given name already exists");
  }

  @Test
  public void testCreateMeasureToHaveUpdatedMeasurementPeriods() {
    Instant startInstant = Instant.now();
    Instant endInstant = startInstant.plus(2, ChronoUnit.DAYS);
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(startInstant))
            .measurementPeriodEnd(Date.from(endInstant))
            .cqlLibraryName("VTE")
            .build();

    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json(elmJson).build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);
    doNothing().when(terminologyValidationService).validateTerminology(anyString(), anyString());
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);

    Measure savedMeasure = measureService.createMeasure(measureToSave, "john rao", "token", false);
    Instant savedStartInstant = savedMeasure.getMeasurementPeriodStart().toInstant();
    assertEquals(0, savedStartInstant.atZone(ZoneOffset.UTC).getHour());
    assertEquals(0, savedStartInstant.atZone(ZoneOffset.UTC).getMinute());
    assertEquals(0, savedStartInstant.atZone(ZoneOffset.UTC).getSecond());

    Instant savedEndInstant = savedMeasure.getMeasurementPeriodEnd().toInstant();
    assertEquals(23, savedEndInstant.atZone(ZoneOffset.UTC).getHour());
    assertEquals(59, savedEndInstant.atZone(ZoneOffset.UTC).getMinute());
    assertEquals(59, savedEndInstant.atZone(ZoneOffset.UTC).getSecond());
  }

  @Test
  public void testCreateMeasureSetsDefaultTestCaseConfiguration() {
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(40, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(10, ChronoUnit.DAYS)))
            .cqlLibraryName("UniqueLibNameForTestCaseConfig")
            .testCaseConfiguration(null)
            .build();

    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);
    doNothing().when(terminologyValidationService).validateTerminology(anyString(), anyString());
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), nullable(String.class), anyString(), any());
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);
    when(measureRepository.save(any(Measure.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    Measure saved = measureService.createMeasure(measureToSave, "author.user", "token", false);
    assertNotNull(saved.getTestCaseConfiguration(), "TestCaseConfiguration should be initialized");
    assertTrue(
        saved.getTestCaseConfiguration().isRavIncluded(),
        "ravIncluded should default to true when creating a measure");
  }

  @Test
  public void testUpdateMeasureThrowsExceptionForDuplicateLibraryName() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .active(true)
            .build();

    Measure updated = original.toBuilder().cqlLibraryName("Changed_Name").active(true).build();

    List<Measure> measureList = Collections.singletonList(Measure.builder().build());

    when(measureUtil.isCqlLibraryNameChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(true);
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(measureList);

    assertThrows(
        DuplicateKeyException.class,
        () -> measureService.updateMeasure(original, "User1", updated, "Access Token"));
  }

  @Test
  public void testUpdateMeasureThrowsExceptionForInvalidMeasurementPeriod() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .cql("cql")
            .measureName("Measure1")
            .versionId("VersionId")
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .build();

    Measure updated = original.toBuilder().measurementPeriodEnd(null).build();
    when(measureUtil.isCqlLibraryNameChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(false);
    when(measureUtil.isMeasurementPeriodChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(true);

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () -> measureService.updateMeasure(original, "User1", updated, "Access Token"));
  }

  @Test
  public void testUpdateMeasureSavesMeasure() {
    final Instant createdAt = Instant.now().minus(5, ChronoUnit.DAYS);
    final String createdBy = "UserABC";

    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .measureSetId("MeasureSetId")
            .model(ModelType.QI_CORE.getValue())
            .cqlLibraryName("CqlLibraryName")
            .cql("cql")
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .createdAt(createdAt)
            .createdBy(createdBy)
            .measureMetaData(draftMeasureMetaData)
            .lastModifiedAt(createdAt)
            .lastModifiedBy(createdBy)
            .testCaseConfiguration(null)
            .build();

    TestCaseConfiguration newTestCaseConfiguration =
        TestCaseConfiguration.builder()
            .id("test-case-config")
            .sdeIncluded(true)
            .manifestExpansion(
                ManifestExpansion.builder().id("manifest-456").fullUrl("manifest-456-url").build())
            .build();
    Measure updated =
        original.toBuilder()
            .createdAt(Instant.now())
            .createdBy("SomebodyElse")
            .lastModifiedAt(null)
            .lastModifiedBy("Nobody")
            .versionId("VersionId")
            .testCaseConfiguration(newTestCaseConfiguration)
            .measureMetaData(draftMeasureMetaData)
            .build();
    when(measureUtil.isCqlLibraryNameChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(true);
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(new ArrayList<>());
    when(measureUtil.isMeasurementPeriodChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(true);
    when(measureUtil.isMeasureCqlChanged(any(Measure.class), any(Measure.class))).thenReturn(false);
    when(measureRepository.findAndModify(any(Measure.class))).thenReturn(updated);

    Measure output = measureService.updateMeasure(original, "User1", updated, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(updated)));

    verify(measureRepository, times(1)).findAndModify(measureArgumentCaptor.capture());
    Measure persisted = measureArgumentCaptor.getValue();
    assertThat(persisted, is(equalTo(updated)));
    assertThat(persisted.getCreatedAt(), is(equalTo(createdAt)));
    assertThat(persisted.getCreatedBy(), is(equalTo(createdBy)));
    final boolean isLastModifiedUpdated =
        Instant.now().minus(1, ChronoUnit.MINUTES).isBefore(persisted.getLastModifiedAt());
    assertThat(isLastModifiedUpdated, is(true));
    assertThat(persisted.getLastModifiedBy(), is(equalTo("User1")));
    assertNotEquals(persisted.getVersionId(), "VersionId");
    assertEquals(persisted.getMeasureSetId(), "MeasureSetId");
    assertEquals(persisted.getCqlLibraryName(), "CqlLibraryName");
  }

  @Test
  public void testUpdateMeasureSavesMeasureWithUpdatedCql() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .versionId("VersionId")
            .cql("original cql here")
            .model(ModelType.QI_CORE.getValue())
            .measureMetaData(draftMeasureMetaData)
            .errors(List.of(MeasureErrorType.ERRORS_ELM_JSON))
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .build();

    Measure updated = original.toBuilder().cql("changed cql here").build();
    when(measureUtil.isCqlLibraryNameChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(false);
    when(measureUtil.isMeasurementPeriodChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(false);
    when(measureUtil.isMeasureCqlChanged(any(Measure.class), any(Measure.class))).thenReturn(true);
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);

    Measure expected =
        updated.toBuilder().error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES).build();
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class))).thenReturn(expected);
    when(measureRepository.findAndModify(any(Measure.class))).thenReturn(expected);

    Measure output = measureService.updateMeasure(original, "User1", updated, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(expected)));

    verify(measureRepository, times(1)).findAndModify(measureArgumentCaptor.capture());
    Measure persisted = measureArgumentCaptor.getValue();
    assertThat(persisted, is(equalTo(expected)));
  }

  @Test
  public void testUpdateMeasureSavesMeasureWithUpdatedCqlAndErrors() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .versionId("VersionId")
            .cql("original cql here")
            .model(ModelType.QI_CORE.getValue())
            .measureMetaData(draftMeasureMetaData)
            .errors(List.of(MeasureErrorType.ERRORS_ELM_JSON))
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .build();

    Measure updated = original.toBuilder().cql("changed cql here").build();
    when(measureUtil.isCqlLibraryNameChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(false);
    when(measureUtil.isMeasurementPeriodChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(false);
    when(measureUtil.isMeasureCqlChanged(any(Measure.class), any(Measure.class))).thenReturn(true);
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);

    Measure expected =
        updated.toBuilder()
            .cqlErrors(true)
            .error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES)
            .build();
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class))).thenReturn(expected);
    when(measureRepository.findAndModify(any(Measure.class))).thenReturn(expected);

    Measure output = measureService.updateMeasure(original, "User1", updated, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(expected)));

    verify(measureRepository, times(1)).findAndModify(measureArgumentCaptor.capture());
    Measure persisted = measureArgumentCaptor.getValue();
    assertThat(persisted, is(equalTo(expected)));
  }

  @Test
  public void testUpdateMeasureSavesMeasureWithUpdatedCqlAndErrorsGettingElm() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .versionId("VersionId")
            .model(ModelType.QI_CORE.getValue())
            .cql("original cql here")
            .measureMetaData(draftMeasureMetaData)
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .build();

    Measure updated = original.toBuilder().cql("changed cql here").build();
    when(measureUtil.isCqlLibraryNameChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(false);
    when(measureUtil.isMeasurementPeriodChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(false);
    when(measureUtil.isMeasureCqlChanged(any(Measure.class), any(Measure.class))).thenReturn(true);
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(true);

    when(measureRepository.findAndModify(any(Measure.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

    Measure output = measureService.updateMeasure(original, "User1", updated, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.isCqlErrors(), is(true));
    assertThat(output.getErrors().contains(MeasureErrorType.ERRORS_ELM_JSON), is(true));

    verify(measureRepository, times(1)).findAndModify(measureArgumentCaptor.capture());
    Measure persisted = measureArgumentCaptor.getValue();
    assertThat(persisted.getErrors(), is(notNullValue()));
    assertThat(persisted.isCqlErrors(), is(true));
    assertThat(persisted.getErrors().contains(MeasureErrorType.ERRORS_ELM_JSON), is(true));
  }

  @Test
  public void testUpdateElmReturnsMeasureUnchangedForNullCql() {
    final Measure measure = Measure.builder().cql(null).build();
    Measure output = measureService.updateElm(measure, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(measure)));
  }

  @Test
  public void testUpdateElmReturnsMeasureUnchangedForEmptyCql() {
    final Measure measure = Measure.builder().cql("").build();
    Measure output = measureService.updateElm(measure, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(measure)));
  }

  @Test
  public void testUpdateElmThrowsExceptionIfElmHasErrors() {
    final Measure measure =
        Measure.builder()
            .cql("some really good cql here")
            .model(ModelType.QDM_5_6.getValue())
            .build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(true);
    assertThrows(
        CqlElmTranslationErrorException.class,
        () -> measureService.updateElm(measure, "Access Token"));
  }

  @Test
  public void testUpdateElmReturnsElmJson() {
    final Measure measure =
        Measure.builder()
            .cql("some really good cql here")
            .model(ModelType.QDM_5_6.getValue())
            .build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    Measure output = measureService.updateElm(measure, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output.getElmJson(), is(equalTo("{\"library\": {}}")));
    assertThat(output.getElmXml(), is(equalTo("<library></library>")));
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
  public void testCheckDuplicateCqlLibraryNameDoesNotThrowException() {
    List<Measure> measureOpt = new ArrayList<>();
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(measureOpt);
    measureService.checkDuplicateCqlLibraryName("testCQLLibraryName");
    verify(measureRepository, times(1)).findAllByCqlLibraryName(eq("testCQLLibraryName"));
  }

  @Test
  public void testCheckDuplicateCqlLibraryNameThrowsExceptionForExistingName() {
    final Measure measure =
        Measure.builder().cqlLibraryName("testCQLLibraryName").active(true).build();
    final List<Measure> measureOpt = Collections.singletonList(measure);
    // Optional<Measure> measureOpt = Optional.of(measure);
    when(measureRepository.findAllByCqlLibraryName(anyString())).thenReturn(measureOpt);
    assertThrows(
        DuplicateKeyException.class,
        () -> measureService.checkDuplicateCqlLibraryName("testCQLLibraryName"));
  }

  @Test
  public void testChangeOwnership() {
    Principal principal = mock(Principal.class);
    MeasureSet measureSet = MeasureSet.builder().measureSetId("123").owner("currentUserId").build();
    Measure measure =
        Measure.builder().id("123").measureSetId("123").measureSet(measureSet).build();
    Optional<Measure> persistedMeasure = Optional.of(measure);
    when(principal.getName()).thenReturn("testUser");
    when(measureRepository.findById(anyString())).thenReturn(persistedMeasure);
    when(measureSetService.changeOwnership(
            anyString(), anyString(), anyBoolean(), anyString(), anyString()))
        .thenReturn(new MeasureSet());

    measureService.changeOwnership(
        measure.getId(), "updatedUserId", true, principal.getName(), ACCESS_TOKEN);

    verify(measureSetService, times(1))
        .changeOwnership(
            measure.getMeasureSetId(), "updatedUserId", true, principal.getName(), ACCESS_TOKEN);
  }

  @Test
  public void testChangeOwnershipPersistedMeasureDoesNotExist() {
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());

    String username = "admin";

    assertThrows(
        ResourceNotFoundException.class,
        () -> {
          measureService.changeOwnership("testMeasureId", "user123", true, username, ACCESS_TOKEN);
        });
  }

  @Test
  public void testChangeOwnershipMeasureSetNotFound() {
    Principal principal = mock(Principal.class);
    MeasureSet measureSet = MeasureSet.builder().measureSetId("123").owner("currentUserId").build();
    Measure measure =
        Measure.builder().id("123").measureSetId("123").measureSet(measureSet).build();
    Optional<Measure> persistedMeasure = Optional.of(measure);

    when(principal.getName()).thenReturn("testUser");
    when(measureRepository.findById(anyString())).thenReturn(persistedMeasure);

    // Simulate ResourceNotFoundException thrown by measureSetService
    doThrow(new ResourceNotFoundException("MeasureSet", "123"))
        .when(measureSetService)
        .changeOwnership(anyString(), anyString(), anyBoolean(), anyString(), anyString());

    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                measureService.changeOwnership(
                    measure.getId(), "updatedUserId", true, principal.getName(), ACCESS_TOKEN));

    assertEquals("Could not find MeasureSet with id: 123", exception.getMessage());

    verify(measureSetService, times(1))
        .changeOwnership(
            measure.getMeasureSetId(), "updatedUserId", true, principal.getName(), ACCESS_TOKEN);
  }

  @Test
  public void testChangeOwnershipRuntimeException() {
    Principal principal = mock(Principal.class);
    MeasureSet measureSet = MeasureSet.builder().measureSetId("123").owner("currentUserId").build();
    Measure measure =
        Measure.builder().id("123").measureSetId("123").measureSet(measureSet).build();
    Optional<Measure> persistedMeasure = Optional.of(measure);

    when(principal.getName()).thenReturn("testUser");
    when(measureRepository.findById(anyString())).thenReturn(persistedMeasure);

    doThrow(new RuntimeException("Error occurred during measure ownership transfer"))
        .when(measureSetService)
        .changeOwnership(anyString(), anyString(), anyBoolean(), anyString(), anyString());

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                measureService.changeOwnership(
                    measure.getId(), "updatedUserId", true, principal.getName(), ACCESS_TOKEN));

    assertEquals("Error occurred during measure ownership transfer", exception.getMessage());

    verify(measureSetService, times(1))
        .changeOwnership(
            measure.getMeasureSetId(), "updatedUserId", true, principal.getName(), ACCESS_TOKEN);
  }

  @Test
  public void testGetAllMeasureIdsAnyDraftStatus() {
    when(measureRepository.findAllMeasureIdsByActive()).thenReturn(List.of(measure1, measure2));
    List<String> result = measureService.getAllActiveMeasureIds(false);

    assertThat(result.size(), is(equalTo(2)));
    assertThat(result.get(0), is(equalTo(measure1.getId())));
    assertThat(result.get(1), is(equalTo(measure2.getId())));
  }

  @Test
  public void testGetAllMeasureIdsDraftOnly() {
    when(measureRepository.findAllMeasureIdsByActiveAndMeasureMetaDataDraft(anyBoolean()))
        .thenReturn(List.of(measure1, measure2));
    List<String> result = measureService.getAllActiveMeasureIds(true);

    assertThat(result.size(), is(equalTo(2)));
    assertThat(result.get(0), is(equalTo(measure1.getId())));
    assertThat(result.get(1), is(equalTo(measure2.getId())));
  }

  @Test
  public void testUpdateReferencesNullMetaData() {
    MeasureMetaData metaData = null;
    measureService.updateReferences(metaData);
    assertNull(metaData);
  }

  @Test
  public void testUpdateReferencesNullReferences() {
    MeasureMetaData metaData = MeasureMetaData.builder().build();
    measureService.updateReferences(metaData);
    assertNotNull(metaData);
    assertNull(metaData.getReferences());
  }

  @Test
  void testFindAllByMeasureSetId() {
    when(measureRepository.findAllByMeasureSetIdAndActive(anyString(), anyBoolean()))
        .thenReturn(List.of(measure1, measure2));

    List<Measure> results = measureService.findAllByMeasureSetId("testMeasureSetId1");

    assertEquals(2, results.size());
  }

  @Test
  void testDeleteVersionedMeasuresOnlyVersionedMeasuresDeleted() {
    measure1.setId("testId1");
    measure1.setMeasureMetaData(MeasureMetaData.builder().draft(false).build());
    measure2.setId("testId2");
    measure2.setMeasureMetaData(MeasureMetaData.builder().draft(true).build());

    ArgumentCaptor<List<Measure>> repositoryArgCaptor = ArgumentCaptor.forClass(List.class);
    measureService.deleteVersionedMeasures(List.of(measure1, measure2));
    verify(measureRepository, times(1)).deleteAll(repositoryArgCaptor.capture());

    List<Measure> deletedMeasures = repositoryArgCaptor.getValue();
    // measure1 is versioned and only measure1 is deleted:
    assertEquals(1, deletedMeasures.size());
    assertEquals("testId1", deletedMeasures.get(0).getId());
    assertEquals("IDIDID", deletedMeasures.get(0).getMeasureSetId());
  }

  @Test
  void testDeleteVersionedMeasuresNotDeletedMetaDataNull() {
    ArgumentCaptor<List<Measure>> repositoryArgCaptor = ArgumentCaptor.forClass(List.class);
    measureService.deleteVersionedMeasures(List.of(measure1, measure2));
    verify(measureRepository, times(0)).deleteAll(repositoryArgCaptor.capture());
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenMeasuresWithGivenIdNotFound() {
    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.associateCmsId("OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenUserIsNotOwnerOfTheMeasures() {
    MeasureSet measureSet = MeasureSet.builder().owner("owner").build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        UnauthorizedException.class,
        () -> measureService.associateCmsId("newowner", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenBothTheMeasureAreQICore() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        InvalidRequestException.class,
        () -> measureService.associateCmsId("OWNER", "qiCoreMeasureId", "qiCoreMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenBothTheMeasureAreQDM() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        InvalidRequestException.class,
        () -> measureService.associateCmsId("OWNER", "qdmMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenQDMMeasureHasNoCmsId() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        InvalidRequestException.class,
        () -> measureService.associateCmsId("OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenQICoreMeasureHasCmsId() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId(anyString())).thenReturn(measureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> measureService.associateCmsId("OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenQICoreMeasureIsVersioned() {
    measure1.setMeasureMetaData(finalMeasureMetaData);
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId("IDIDID")).thenReturn(qiCoreMeasureSet);
    when(measureSetService.findByMeasureSetId("2D2D2D")).thenReturn(qdmMeasureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> measureService.associateCmsId("OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdThrowsExceptionWhenAnyQICoreMeasureHasSameCmsId() {
    Measure qiCoreMeasure =
        Measure.builder()
            .model(ModelType.QI_CORE.getValue())
            .measureSetId("NewIDIDID")
            .measureMetaData(draftMeasureMetaData)
            .build();
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId("IDIDID")).thenReturn(qiCoreMeasureSet);
    when(measureSetService.findByMeasureSetId("2D2D2D")).thenReturn(qdmMeasureSet);
    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(List.of(qiCoreMeasure));

    assertThrows(
        InvalidResourceStateException.class,
        () -> measureService.associateCmsId("OWNER", "qiCoreMeasureId", "qdmMeasureId", false));
  }

  @Test
  public void testAssociateCmsIdSuccessfullyWithoutCpyingMetaData() {

    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet updatedQiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").cmsId(12).owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId("IDIDID")).thenReturn(qiCoreMeasureSet);
    when(measureSetService.findByMeasureSetId("2D2D2D")).thenReturn(qdmMeasureSet);

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(List.of());
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedQiCoreMeasureSet);

    MeasureSet updatedMeasureSet =
        measureService.associateCmsId("OWNER", "qiCoreMeasureId", "qdmMeasureId", false);
    assertThat(updatedMeasureSet.getOwner(), is(equalTo(updatedQiCoreMeasureSet.getOwner())));
    assertThat(
        updatedMeasureSet.getMeasureSetId(),
        is(equalTo(updatedQiCoreMeasureSet.getMeasureSetId())));
    assertThat(updatedMeasureSet.getCmsId(), is(equalTo(updatedQiCoreMeasureSet.getCmsId())));
  }

  @Test
  public void testAssociateCmsIdSuccessfullyWithCpyingMetaData() {

    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet updatedQiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").cmsId(12).owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();
    when(measureRepository.findById("qiCoreMeasureId")).thenReturn(Optional.of(measure1));
    when(measureRepository.findById("qdmMeasureId")).thenReturn(Optional.of(measure2));
    when(measureSetService.findByMeasureSetId("IDIDID")).thenReturn(qiCoreMeasureSet);
    when(measureSetService.findByMeasureSetId("2D2D2D")).thenReturn(qdmMeasureSet);

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(List.of());
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedQiCoreMeasureSet);

    MeasureSet updatedMeasureSet =
        measureService.associateCmsId("OWNER", "qiCoreMeasureId", "qdmMeasureId", true);
    assertThat(updatedMeasureSet.getOwner(), is(equalTo(updatedQiCoreMeasureSet.getOwner())));
    assertThat(
        updatedMeasureSet.getMeasureSetId(),
        is(equalTo(updatedQiCoreMeasureSet.getMeasureSetId())));
    assertThat(updatedMeasureSet.getCmsId(), is(equalTo(updatedQiCoreMeasureSet.getCmsId())));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionForNullQiCoreMeasure() {
    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.validateCmsIdAssociation("OWNER", null, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionForNullQdmMeasure() {
    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.validateCmsIdAssociation("OWNER", measure1, null));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenBothTheMeasureAreQiCore411() {
    assertThrows(
        InvalidRequestException.class,
        () -> measureService.validateCmsIdAssociation("OWNER", measure1, measure1));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenBothTheMeasureAreQiCore600() {
    measureList.setModel(ModelType.QI_CORE_6_0_0.getValue());
    assertThrows(
        InvalidRequestException.class,
        () -> measureService.validateCmsIdAssociation("OWNER", measure1, measure1));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenBothTheMeasureAreQDM() {
    assertThrows(
        InvalidRequestException.class,
        () -> measureService.validateCmsIdAssociation("OWNER", measure1, measure1));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenUsernameIsNotOwner() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    measure1.setMeasureSet(measureSet);
    measure2.setMeasureSet(measureSet);

    assertThrows(
        UnauthorizedException.class,
        () -> measureService.validateCmsIdAssociation("NOT_OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenQDMMeasureHasNoCmsId() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").build();
    measure1.setMeasureSet(measureSet);
    measure2.setMeasureSet(measureSet);

    assertThrows(
        InvalidRequestException.class,
        () -> measureService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenQICoreMeasureHasCmsId() {
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").cmsId(12).build();
    measure1.setMeasureSet(measureSet);
    measure2.setMeasureSet(measureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> measureService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenQICoreMeasureIsVersioned() {
    measure1.setMeasureMetaData(finalMeasureMetaData);
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    measure1.setMeasureSet(qiCoreMeasureSet);
    measure2.setMeasureSet(qdmMeasureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> measureService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationThrowsExceptionWhenAnyQICoreMeasureHasSameCmsId() {
    Measure qiCoreMeasure =
        Measure.builder()
            .model(ModelType.QI_CORE.getValue())
            .measureSetId("NewIDIDID")
            .measureMetaData(draftMeasureMetaData)
            .build();
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(List.of(qiCoreMeasure));

    measure1.setMeasureSet(qiCoreMeasureSet);
    measure2.setMeasureSet(qdmMeasureSet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> measureService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociation() {
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(Collections.emptyList());

    measure1.setMeasureSet(qiCoreMeasureSet);
    measure2.setMeasureSet(qdmMeasureSet);

    when(measureLockService.checkMeasureLock(anyString(), any(Measure.class), anyString()))
        .thenReturn(false);

    assertDoesNotThrow(() -> measureService.validateCmsIdAssociation("OWNER", measure1, measure2));
  }

  @Test
  public void testValidateCmsIdAssociationWhenMeasureIsLocked() {
    MeasureSet qiCoreMeasureSet =
        MeasureSet.builder().measureSetId("IDIDID").owner("OWNER").build();
    MeasureSet qdmMeasureSet =
        MeasureSet.builder().measureSetId("2D2D2D").owner("OWNER").cmsId(12).build();

    when(measureRepository.findAllByModelAndCmsId(any(String.class), any(Integer.class)))
        .thenReturn(Collections.emptyList());

    measure1.setMeasureSet(qiCoreMeasureSet);
    measure2.setMeasureSet(qdmMeasureSet);

    when(measureLockService.checkMeasureLock(anyString(), any(Measure.class), anyString()))
        .thenThrow(
            new LockNotObtainedException(
                "Unable to associate measure. Locked while being edited by another.user"));

    Exception exception =
        assertThrows(
            LockNotObtainedException.class,
            () -> measureService.validateCmsIdAssociation("OWNER", measure1, measure2));

    assertThat(
        exception.getMessage(),
        is(equalTo("Unable to associate measure. Locked while being edited by another.user")));
  }

  @Test
  void testFindLibraryUsage() {
    String libraryName = "test";
    String owner = "john";
    LibraryUsage usage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    when(measureRepository.findLibraryUsageByLibraryName(anyString())).thenReturn(List.of(usage));
    List<LibraryUsage> libraryUsages = measureService.findLibraryUsage(libraryName);
    assertThat(libraryUsages.size(), is(equalTo(1)));
    assertThat(libraryUsages.get(0).getName(), is(equalTo(libraryName)));
    assertThat(libraryUsages.get(0).getOwner(), is(equalTo(owner)));
  }

  @Test
  void testFindLibraryUsageWhenLibraryNameBlank() {
    Exception ex =
        assertThrows(InvalidRequestException.class, () -> measureService.findLibraryUsage(null));
    assertThat(ex.getMessage(), is(equalTo("Please provide library name.")));
  }

  @Test
  public void testUpdateMeasureDefinitionIdNewDefinition() {
    MeasureMetaData metaData =
        MeasureMetaData.builder()
            .measureDefinitions(
                List.of(
                    MeasureDefinition.builder()
                        .term("test term")
                        .definition("test definition")
                        .build()))
            .build();
    measureService.updateMeasureDefinitions(metaData);
    assertNotNull(metaData);
    assertNotNull(metaData.getMeasureDefinitions());
    assertNotNull(metaData.getMeasureDefinitions().get(0).getId());
  }

  @Test
  public void testUpdateMeasureDefinitionsNullDefinitions() {
    MeasureMetaData metaData = MeasureMetaData.builder().build();
    measureService.updateMeasureDefinitions(metaData);
    assertNotNull(metaData);
    assertNull(metaData.getMeasureDefinitions());
  }

  @Test
  public void testUpdateDefinitionIdNullMetaData() {
    MeasureMetaData metaData = null;
    measureService.updateMeasureDefinitions(metaData);
    assertNull(metaData);
  }

  @Test
  public void testGetSharedMeasuresWithNoMeasureFound() {
    String measureId1 = "measureId1";
    List<String> measureIds = List.of(measureId1);

    when(measureService.findMeasureById(eq(measureId1))).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.getSharedMeasures(measureIds, "username"));
  }

  @Test
  public void testGetSharedMeasuresWithNoMeasureSetFound() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("userId2");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    MeasureSet measureSet1 =
        MeasureSet.builder()
            .measureSetId("measureSetId1")
            .owner("testUser")
            .acls(List.of(acl1))
            .build();

    String measureId1 = "measureId1";
    Measure measure1 =
        Measure.builder()
            .id(measureId1)
            .measureSetId(measureSet1.getMeasureSetId())
            .measureSet(measureSet1)
            .build();

    String measureId2 = "measureId2";
    Measure measure2 = Measure.builder().id(measureId2).build();

    List<String> measureIds = List.of(measureId1, measureId2);

    when(measureService.findMeasureById(eq(measureId1))).thenReturn(measure1);
    when(measureService.findMeasureById(eq(measureId2))).thenReturn(measure2);

    assertThrows(
        InvalidMeasureStateException.class,
        () -> measureService.getSharedMeasures(measureIds, "username"));
  }

  @Test
  public void testGetSharedMeasuresWithNoMeasureSetAclsFoundForOneMeasure() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("userId1");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    MeasureSet measureSet1 =
        MeasureSet.builder()
            .measureSetId("measureSetId1")
            .owner("testUser")
            .acls(List.of(acl1))
            .build();

    String measureId1 = "measureId1";
    Measure measure1 =
        Measure.builder()
            .id(measureId1)
            .measureSetId(measureSet1.getMeasureSetId())
            .measureSet(measureSet1)
            .build();

    MeasureSet measureSet2 =
        MeasureSet.builder().measureSetId("measureSetId1").owner("testUser").build();

    String measureId2 = "measureId2";
    Measure measure2 =
        Measure.builder()
            .id(measureId1)
            .measureSetId(measureSet1.getMeasureSetId())
            .measureSet(measureSet2)
            .build();

    Instant fixedInstant = Instant.parse("2025-03-17T10:00:00Z");
    ZoneId utc = ZoneId.of("UTC");
    Clock fixedClock = Clock.fixed(fixedInstant, utc);

    MeasureSetActionLog measureSetActionLog =
        MeasureSetActionLog.builder()
            .actions(
                List.of(
                    AccessControlAction.builder()
                        .sharedWith(acl1.getUserId())
                        .actionType(ActionType.SHARED)
                        .performedAt(fixedClock.instant())
                        .performedBy("performedByUserId")
                        .build()))
            .build();

    List<String> measureIds = List.of(measureId1, measureId2);

    when(measureService.findMeasureById(eq(measureId1))).thenReturn(measure1);
    when(measureService.findMeasureById(eq(measureId2))).thenReturn(measure2);
    when(actionLogService.findMeasureSetActionLogByTargetId(anyString()))
        .thenReturn(measureSetActionLog);

    Map<String, List<SharedUser>> sharedMeasures =
        measureService.getSharedMeasures(measureIds, "username");

    assertThat(sharedMeasures.size(), is(equalTo(2)));

    assertTrue(sharedMeasures.containsKey(measureId1));
    assertThat(sharedMeasures.get(measureId1).size(), is(equalTo(1)));
    assertThat(
        sharedMeasures.get(measureId1).get(0).getUserId(),
        is(equalTo(measure1.getMeasureSet().getAcls().get(0).getUserId())));
    assertThat(
        sharedMeasures.get(measureId1).get(0).getPerformedAt(),
        is(equalTo(measureSetActionLog.getActions().get(0).getPerformedAt())));

    assertTrue(sharedMeasures.containsKey(measureId2));
    assertThat(sharedMeasures.get(measureId2).size(), is(equalTo(0)));
  }

  @Test
  public void testGetSharedMeasures() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("userId1");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("userId2");
    acl2.setRoles(Set.of(RoleEnum.SHARED_WITH));

    MeasureSet measureSet1 =
        MeasureSet.builder()
            .measureSetId("measureSetId1")
            .owner("testUser")
            .acls(List.of(acl2, acl1))
            .build();

    String measureId1 = "measureId1";
    Measure measure1 =
        Measure.builder()
            .id(measureId1)
            .measureSetId(measureSet1.getMeasureSetId())
            .measureSet(measureSet1)
            .build();

    MeasureSet measureSet2 =
        MeasureSet.builder()
            .measureSetId("measureSetId1")
            .owner("testUser")
            .acls(List.of(acl1))
            .build();

    String measureId2 = "measureId2";
    Measure measure2 =
        Measure.builder()
            .id(measureId1)
            .measureSetId(measureSet1.getMeasureSetId())
            .measureSet(measureSet2)
            .build();

    Instant fixedInstant = Instant.parse("2025-03-17T10:00:00Z");
    ZoneId utc = ZoneId.of("UTC");
    Clock fixedClock = Clock.fixed(fixedInstant, utc);

    MeasureSetActionLog measureSetActionLog =
        MeasureSetActionLog.builder()
            .actions(
                List.of(
                    AccessControlAction.builder()
                        .sharedWith(acl1.getUserId())
                        .actionType(ActionType.SHARED)
                        .performedAt(fixedClock.instant())
                        .performedBy("performedByUserId")
                        .build()))
            .build();

    List<String> measureIds = List.of(measureId1, measureId2);

    when(measureService.findMeasureById(eq(measureId1))).thenReturn(measure1);
    when(measureService.findMeasureById(eq(measureId2))).thenReturn(measure2);
    when(actionLogService.findMeasureSetActionLogByTargetId(anyString()))
        .thenReturn(measureSetActionLog);

    Map<String, List<SharedUser>> sharedMeasures =
        measureService.getSharedMeasures(measureIds, "username");

    assertThat(sharedMeasures.size(), is(equalTo(2)));

    assertTrue(sharedMeasures.containsKey(measureId1));
    assertThat(sharedMeasures.get(measureId1).size(), is(equalTo(2)));
    assertThat(
        sharedMeasures.get(measureId1).get(0).getUserId(),
        is(equalTo(measure1.getMeasureSet().getAcls().get(0).getUserId())));
    assertThat(sharedMeasures.get(measureId1).get(0).getPerformedAt(), is(equalTo(null)));
    assertThat(
        sharedMeasures.get(measureId1).get(1).getUserId(),
        is(equalTo(measure2.getMeasureSet().getAcls().get(0).getUserId())));
    assertThat(
        sharedMeasures.get(measureId1).get(1).getPerformedAt(),
        is(equalTo(measureSetActionLog.getActions().get(0).getPerformedAt())));

    assertTrue(sharedMeasures.containsKey(measureId2));
    assertThat(sharedMeasures.get(measureId1).size(), is(equalTo(2)));
    assertThat(
        sharedMeasures.get(measureId2).get(0).getUserId(),
        is(equalTo(measure2.getMeasureSet().getAcls().get(0).getUserId())));
    assertThat(
        sharedMeasures.get(measureId2).get(0).getPerformedAt(),
        is(equalTo(measureSetActionLog.getActions().get(0).getPerformedAt())));
  }

  @Test
  public void testShareMeasuresWithNoMeasureFound() {
    Map<String, List<String>> measureUserIdMap = new HashMap<>();

    String measureId1 = "measureId1";
    String measureId2 = "measureId2";

    measureUserIdMap.put(measureId1, List.of("userId1", "userId2"));
    measureUserIdMap.put(measureId2, List.of("userId2"));

    when(measureService.findMeasureById(eq(measureId1))).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.shareMeasures(measureUserIdMap, "userName", "accessToken"));
  }

  @Test
  public void testShareMeasures() {
    Map<String, List<String>> measureUserIdMap = new HashMap<>();

    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("userId1");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("userId2");
    acl2.setRoles(Set.of(RoleEnum.SHARED_WITH));

    MeasureSet measureSet1 =
        MeasureSet.builder()
            .measureSetId("measureSetId1")
            .owner("testUser")
            .acls(List.of(acl1, acl2))
            .build();

    String measureId1 = "measureId1";
    Measure measure1 =
        Measure.builder()
            .id(measureId1)
            .measureSetId(measureSet1.getMeasureSetId())
            .measureSet(measureSet1)
            .build();

    MeasureSet measureSet2 =
        MeasureSet.builder()
            .measureSetId("measureSetId1")
            .owner("testUser")
            .acls(List.of(acl1, acl2))
            .build();

    String measureId2 = "measureId2";
    Measure measure2 =
        Measure.builder()
            .id(measureId1)
            .measureSetId(measureSet1.getMeasureSetId())
            .measureSet(measureSet2)
            .build();

    measureUserIdMap.put(measureId1, List.of("userId1", "userId2"));
    measureUserIdMap.put(measureId2, List.of("userId2"));

    when(measureService.findMeasureById(eq(measureId1))).thenReturn(measure1);
    when(measureService.findMeasureById(eq(measureId2))).thenReturn(measure2);

    doNothing().when(measureService).verifyAuthorization(anyString(), any(), any());

    AclSpecification aclSpecification1 =
        AclSpecification.builder().userId("userId1").roles(Set.of(RoleEnum.SHARED_WITH)).build();
    AclSpecification aclSpecification2 =
        AclSpecification.builder().userId("userId2").roles(Set.of(RoleEnum.SHARED_WITH)).build();

    doReturn(List.of(aclSpecification1, aclSpecification2))
        .when(measureService)
        .updateAccessControlList(
            anyString(), any(AclOperation.class), anyString(), anyBoolean(), anyString());

    Map<String, List<AclSpecification>> measureIdToAclSpecification =
        measureService.shareMeasures(measureUserIdMap, "userName", "accessToken");
    assertThat(measureIdToAclSpecification.size(), is(equalTo(2)));

    assertTrue(measureIdToAclSpecification.containsKey(measureId1));
    assertTrue(measureIdToAclSpecification.containsKey(measureId2));

    assertThat(
        measureIdToAclSpecification.get(measureId1),
        is(equalTo(List.of(aclSpecification1, aclSpecification2))));

    assertThat(
        measureIdToAclSpecification.get(measureId2),
        is(equalTo(List.of(aclSpecification1, aclSpecification2))));
  }

  @Test
  public void testUnshareMeasuresWithNoMeasureFound() {
    Map<String, List<String>> measureUserIdMap = new HashMap<>();

    String measureId1 = "measureId1";
    String measureId2 = "measureId2";

    measureUserIdMap.put(measureId1, List.of("userId1", "userId2"));
    measureUserIdMap.put(measureId2, List.of("userId2"));

    when(measureService.findMeasureById(eq(measureId1))).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.unshareMeasures(measureUserIdMap, "userName", "accessToken"));
  }

  @Test
  public void testUnshareMeasures() {
    Map<String, List<String>> measureUserIdMap = new HashMap<>();

    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("userId1");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("userId2");
    acl2.setRoles(Set.of(RoleEnum.SHARED_WITH));

    MeasureSet measureSet1 =
        MeasureSet.builder()
            .measureSetId("measureSetId1")
            .owner("testUser")
            .acls(List.of(acl1, acl2))
            .build();

    String measureId1 = "measureId1";
    Measure measure1 =
        Measure.builder()
            .id(measureId1)
            .measureSetId(measureSet1.getMeasureSetId())
            .measureSet(measureSet1)
            .build();

    MeasureSet measureSet2 =
        MeasureSet.builder()
            .measureSetId("measureSetId1")
            .owner("testUser")
            .acls(List.of(acl1, acl2))
            .build();

    String measureId2 = "measureId2";
    Measure measure2 =
        Measure.builder()
            .id(measureId1)
            .measureSetId(measureSet1.getMeasureSetId())
            .measureSet(measureSet2)
            .build();

    measureUserIdMap.put(measureId1, List.of("userId2"));
    measureUserIdMap.put(measureId2, List.of("userId2"));

    when(measureService.findMeasureById(eq(measureId1))).thenReturn(measure1);
    when(measureService.findMeasureById(eq(measureId2))).thenReturn(measure2);

    doNothing().when(measureService).verifyAuthorization(anyString(), any(), any());

    AclSpecification aclSpecification1 =
        AclSpecification.builder().userId("userId1").roles(Set.of(RoleEnum.SHARED_WITH)).build();

    doReturn(List.of(aclSpecification1))
        .when(measureService)
        .updateAccessControlList(
            anyString(), any(AclOperation.class), anyString(), anyBoolean(), anyString());

    Map<String, List<AclSpecification>> measureIdToAclSpecification =
        measureService.unshareMeasures(measureUserIdMap, "userName", "accessToken");
    assertThat(measureIdToAclSpecification.size(), is(equalTo(2)));

    assertTrue(measureIdToAclSpecification.containsKey(measureId1));
    assertTrue(measureIdToAclSpecification.containsKey(measureId2));

    assertThat(
        measureIdToAclSpecification.get(measureId1), is(equalTo(List.of(aclSpecification1))));

    assertThat(
        measureIdToAclSpecification.get(measureId2), is(equalTo(List.of(aclSpecification1))));
  }

  @Test
  public void filtersStratificationsForQiCoreModel() {
    Stratification strat1 = new Stratification();
    strat1.setAssociations(List.of(PopulationType.INITIAL_POPULATION));
    Stratification strat2 = new Stratification();
    strat2.setAssociations(Collections.emptyList());
    List<Group> groups = List.of(Group.builder().stratifications(List.of(strat1, strat2)).build());

    Measure updatingMeasure = new Measure();
    updatingMeasure.setModel(ModelType.QI_CORE.getValue());
    updatingMeasure.setGroups(groups);
    updatingMeasure.setCql("cql");

    measureService.updateMeasure(new Measure(), "user", updatingMeasure, "token");

    assertEquals(1, groups.get(0).getStratifications().size());
    assertTrue(groups.get(0).getStratifications().contains(strat1));
  }

  @Test
  public void filtersStratificationsForQDMModel() {
    Stratification strat1 = new Stratification();
    strat1.setCqlDefinition("cql definition");
    Stratification strat2 = new Stratification();
    strat2.setCqlDefinition("");
    List<Group> groups = List.of(Group.builder().stratifications(List.of(strat1, strat2)).build());

    Measure updatingMeasure = new Measure();
    updatingMeasure.setModel(ModelType.QDM_5_6.getValue());
    updatingMeasure.setGroups(groups);
    updatingMeasure.setCql("cql");

    measureService.updateMeasure(new Measure(), "user", updatingMeasure, "token");

    assertEquals(1, groups.get(0).getStratifications().size());
    assertTrue(groups.get(0).getStratifications().contains(strat1));
  }

  @Test
  public void doesNotFilterStratificationsWhenGroupsAreEmpty() {
    Measure updatingMeasure = new Measure();
    updatingMeasure.setModel(ModelType.QI_CORE.getValue());
    updatingMeasure.setGroups(Collections.emptyList());
    updatingMeasure.setCql("cql");

    measureService.updateMeasure(new Measure(), "user", updatingMeasure, "token");

    assertTrue(updatingMeasure.getGroups().isEmpty());
  }

  @Test
  public void doesNotFilterStratificationsWhenStratificationsAreEmpty() {
    Group group = new Group();
    group.setStratifications(Collections.emptyList());

    Measure updatingMeasure = new Measure();
    updatingMeasure.setModel(ModelType.QI_CORE.getValue());
    updatingMeasure.setGroups(List.of(group));
    updatingMeasure.setCql("cql");

    measureService.updateMeasure(new Measure(), "user", updatingMeasure, "token");

    assertTrue(group.getStratifications().isEmpty());
  }

  @Test
  void updateMeasureTestCaseConfigurationSuccessfullyUpdatesMeasure() {
    String username = "testUser";
    String measureId = "testMeasureId";
    TestCaseConfiguration testCaseConfig = new TestCaseConfiguration();
    Measure existingMeasure =
        Measure.builder()
            .id(measureId)
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .build();
    Measure updatedMeasure = Measure.builder().id(measureId).build();

    when(measureRepository.findByIdAndActive(measureId, true))
        .thenReturn(Optional.of(existingMeasure));
    doNothing().when(measureService).verifyAuthorization(username, existingMeasure);
    when(testCasePatchRepository.findAndModifyTestCaseConfig(testCaseConfig, measureId))
        .thenReturn(updatedMeasure);

    Measure result =
        measureService.updateMeasureTestCaseConfiguration(username, measureId, testCaseConfig);

    assertNotNull(result);
    assertEquals(updatedMeasure, result);
    verify(measureService, times(1)).verifyAuthorization(username, existingMeasure);
    verify(testCasePatchRepository, times(1))
        .findAndModifyTestCaseConfig(testCaseConfig, measureId);
  }

  @Test
  void updateMeasureTestCaseConfigurationThrowsInvalidIdExceptionForNullId() {
    String username = "testUser";
    TestCaseConfiguration testCaseConfig = new TestCaseConfiguration();

    assertThrows(
        InvalidIdException.class,
        () -> measureService.updateMeasureTestCaseConfiguration(username, null, testCaseConfig));
  }

  @Test
  void updateMeasureTestCaseConfigurationThrowsInvalidIdExceptionForEmptyId() {
    String username = "testUser";
    TestCaseConfiguration testCaseConfig = new TestCaseConfiguration();

    assertThrows(
        InvalidIdException.class,
        () -> measureService.updateMeasureTestCaseConfiguration(username, "", testCaseConfig));
  }

  @Test
  void findActiveMeasureByIdReturnsMeasureWhenIdExists() {
    String measureId = "existingMeasureId";
    Measure measure = Measure.builder().id(measureId).active(true).build();

    when(measureRepository.findByIdAndActive(measureId, true)).thenReturn(Optional.of(measure));

    Measure result = measureService.findActiveMeasureById(measureId);

    assertNotNull(result);
    assertEquals(measureId, result.getId());
    verify(measureRepository, times(1)).findByIdAndActive(measureId, true);
  }

  @Test
  void findActiveMeasureByIdThrowsResourceNotFoundExceptionWhenIdDoesNotExist() {
    String measureId = "nonExistingMeasureId";

    when(measureRepository.findByIdAndActive(measureId, true)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.findActiveMeasureById(measureId),
        "Expected ResourceNotFoundException for non-existing measureId");
    verify(measureRepository, times(1)).findByIdAndActive(measureId, true);
  }

  @Test
  void findActiveMeasureByIdThrowsResourceNotFoundExceptionForNullId() {
    when(measureRepository.findByIdAndActive(null, true)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.findActiveMeasureById(null),
        "Expected ResourceNotFoundException for null measureId");
    verify(measureRepository, times(1)).findByIdAndActive(null, true);
  }

  @Test
  public void testTransferMeasures() {
    MeasureSet measureSet = MeasureSet.builder().measureSetId("123").owner("testUser").build();
    Measure measure =
        Measure.builder().id("123").measureSetId("123").measureSet(measureSet).build();
    Optional<Measure> persistedMeasure = Optional.of(measure);

    when(userServiceClient.getUserDetails(anyString(), anyString()))
        .thenReturn(
            UserDetailsDto.builder().harpId("user123").userStatus(UserStatus.ACTIVE).build());
    when(measureRepository.findById(anyString())).thenReturn(persistedMeasure);
    when(measureSetService.changeOwnership(
            anyString(), anyString(), any(Boolean.class), anyString(), anyString()))
        .thenReturn(new MeasureSet());

    List<String> failed =
        measureService.transferMeasures(
            List.of("123"), "user123", true, "anotherUser", ACCESS_TOKEN);

    assertTrue(failed.isEmpty());
  }

  @Test
  public void testTransferMeasuresNotFound() {
    when(userServiceClient.getUserDetails(anyString(), anyString()))
        .thenReturn(
            UserDetailsDto.builder().harpId("user123").userStatus(UserStatus.ACTIVE).build());
    when(measureRepository.findById("123")).thenReturn(Optional.empty());

    List<String> failed =
        measureService.transferMeasures(
            List.of("123"), "user123", true, "anotherUser", ACCESS_TOKEN);

    assertEquals(1, failed.size());
    assertTrue(failed.contains("123"));
  }

  @Test
  public void testTransferMeasuresThrowsWhenTargetUserNotFound() {
    when(userServiceClient.getUserDetails(anyString(), anyString())).thenReturn(null);

    assertThrows(
        InvalidIdException.class,
        () ->
            measureService.transferMeasures(
                List.of("123"), "user123", true, "anotherUser", ACCESS_TOKEN));

    verify(measureRepository, never()).findById(anyString());
  }

  @Test
  public void testTransferMeasuresThrowsWhenTargetUserIsInactive() {
    when(userServiceClient.getUserDetails(anyString(), anyString()))
        .thenReturn(
            UserDetailsDto.builder().harpId("user123").userStatus(UserStatus.DEACTIVATED).build());

    assertThrows(
        InvalidIdException.class,
        () ->
            measureService.transferMeasures(
                List.of("123"), "user123", true, "anotherUser", ACCESS_TOKEN));

    verify(measureRepository, never()).findById(anyString());
  }

  @Test
  void getMeasureHistoryReturnsHistoryForValidMeasureId() {
    String measureId = "validMeasureId";
    String userName = "testUser";
    Measure measure = Measure.builder().id(measureId).measureSetId("measureSetId").build();
    Action createdAction = new Action();
    createdAction.setActionType(ActionType.CREATED);
    createdAction.setPerformedAt(Instant.now());
    createdAction.setPerformedBy("test.user@gmail.com");
    createdAction.setAdditionalActionMessage("");
    List<Action> actions = List.of(createdAction);

    when(measureRepository.findById(measureId)).thenReturn(Optional.of(measure));
    when(actionLogService.findMeasureHistory(measureId, "measureSetId")).thenReturn(actions);

    List<Action> result = measureService.getMeasureHistory(measureId, userName);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(ActionType.CREATED, result.get(0).getActionType());
    verify(measureRepository, times(1)).findById(measureId);
    verify(actionLogService, times(1)).findMeasureHistory(measureId, "measureSetId");
  }

  @Test
  void getMeasureHistoryThrowsInvalidRequestExceptionForBlankMeasureId() {
    String measureId = " ";
    String userName = "testUser";

    assertThrows(
        InvalidRequestException.class, () -> measureService.getMeasureHistory(measureId, userName));
    verifyNoInteractions(measureRepository, actionLogService);
  }

  @Test
  void getMeasureHistoryThrowsResourceNotFoundExceptionForNonExistentMeasureId() {
    String measureId = "nonExistentMeasureId";
    String userName = "testUser";

    when(measureRepository.findById(measureId)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> measureService.getMeasureHistory(measureId, userName));
    verify(measureRepository, times(1)).findById(measureId);
    verifyNoInteractions(actionLogService);
  }

  // test cases for deactivateMeasure method
  @Test
  public void testDeactivateMeasureWithBlankId() {
    // Given
    String measureId = "   ";
    String username = "test-user";

    // When & Then
    InvalidIdException exception =
        assertThrows(
            InvalidIdException.class, () -> measureService.deactivateMeasure(measureId, username));

    assertThat(exception.getMessage(), is(equalTo("Username and Measure Id is required.")));
  }

  @Test
  public void testDeactivateMeasureWithBlankBlankOwner() {
    // Given
    String measureId = "1";
    String username = " ";

    // When & Then
    InvalidIdException exception =
        assertThrows(
            InvalidIdException.class, () -> measureService.deactivateMeasure(measureId, username));

    assertThat(exception.getMessage(), is(equalTo("Username and Measure Id is required.")));
  }

  @Test
  public void testDeactivateMeasureThatDoesNotExist() {
    String measureId = "1";
    String username = "test-user";
    when(measureService.findMeasureById(measureId)).thenReturn(null);
    Exception exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> measureService.deactivateMeasure(measureId, username));

    assertThat(exception.getMessage(), is(equalTo("Measure does not exist.")));
  }

  @Test
  public void testDeactivateMeasureWhenUserNotAuthorized() {
    // Given
    String measureId = "test-measure-id";
    String username = "unauthorized-user";
    Measure existingMeasure =
        measure1.toBuilder()
            .id(measureId)
            .active(true)
            .measureSet(MeasureSet.builder().owner("test").build())
            .build();

    // When
    when(measureService.findMeasureById(measureId)).thenReturn(existingMeasure);

    // Then
    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class,
            () -> measureService.deactivateMeasure(measureId, username));

    assertThat(
        exception.getMessage(), is(equalTo("User is not authorized to delete this measure.")));
    verify(measureLockService, never()).lockMeasure(anyString(), anyString());
    verify(measureRepository, never()).save(any(Measure.class));
  }

  @Test
  public void testDeactivateVersionedMeasure() {
    // Given
    String measureId = "test-measure-id";
    String username = "test-user";
    Measure existingMeasure =
        measure1.toBuilder()
            .id(measureId)
            .active(true)
            .measureSet(MeasureSet.builder().owner(username).build())
            .measureMetaData(MeasureMetaData.builder().draft(false).build())
            .build();

    // When
    when(measureService.findMeasureById(measureId)).thenReturn(existingMeasure);

    // Then
    Exception exception =
        assertThrows(
            InvalidDraftStatusException.class,
            () -> measureService.deactivateMeasure(measureId, username));

    assertThat(
        exception.getMessage(),
        is(
            equalTo(
                "Response could not be completed for measure with ID test-measure-id, since the measure is not in a draft status")));
  }

  @Test
  public void testDeactivateInactiveMeasure() {
    // Given
    String measureId = "test-measure-id";
    String username = "test-user";
    Measure existingMeasure =
        measure1.toBuilder()
            .id(measureId)
            .active(false)
            .measureSet(MeasureSet.builder().owner(username).build())
            .measureMetaData(draftMeasureMetaData)
            .build();

    // When
    when(measureService.findMeasureById(measureId)).thenReturn(existingMeasure);

    // Then
    Exception exception =
        assertThrows(
            InvalidResourceStateException.class,
            () -> measureService.deactivateMeasure(measureId, username));

    assertThat(exception.getMessage(), is(equalTo("Measure is inactive.")));
  }

  @Test
  public void testDeactivateMeasureWhenMeasureLockExists() {
    // Given
    String measureId = "test-measure-id";
    String currentUser = "test-user";
    String otherUser = "test-user-2";
    Measure existingMeasure =
        measure1.toBuilder()
            .id(measureId)
            .active(true)
            .measureSet(MeasureSet.builder().owner(currentUser).build())
            .measureMetaData(draftMeasureMetaData)
            .build();

    // When
    when(measureService.findMeasureById(measureId)).thenReturn(existingMeasure);
    when(measureLockService.checkMeasureAndTestCaseLock(
            anyString(), any(Measure.class), anyString()))
        .thenThrow(
            new LockNotObtainedException(
                "Unable to delete measure. Locked while being edited by " + otherUser));

    // Then
    Exception exception =
        assertThrows(
            LockNotObtainedException.class,
            () -> measureService.deactivateMeasure(measureId, currentUser));

    assertThat(
        exception.getMessage(),
        is(equalTo("Unable to delete measure. Locked while being edited by " + otherUser)));
  }

  @Test
  public void testDeactivateMeasureSuccessfully() {
    // Given
    String username = "test-user";

    Measure existingMeasure =
        measure1.toBuilder()
            .active(true)
            .measureSet(MeasureSet.builder().owner(username).build())
            .measureMetaData(draftMeasureMetaData)
            .build();

    // When
    when(measureService.findMeasureById(existingMeasure.getId())).thenReturn(existingMeasure);
    when(measureLockService.checkMeasureAndTestCaseLock(
            anyString(), any(Measure.class), anyString()))
        .thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(existingMeasure);
    when(actionLogService.logAction(
            existingMeasure.getId(), Measure.class, ActionType.DELETED, username))
        .thenReturn(true);

    // Then
    Measure result = measureService.deactivateMeasure(existingMeasure.getId(), username);

    assertThat(result, is(notNullValue()));
    assertThat(result.isActive(), is(false));

    verify(measureRepository).save(measureArgumentCaptor.capture());
    Measure savedMeasure = measureArgumentCaptor.getValue();
    assertThat(savedMeasure.isActive(), is(false));
  }

  @Test
  public void testDeactivateMeasureSuccessfullyWhenLockingIsDisabled() {
    // Given
    String username = "test-user";

    Measure existingMeasure =
        measure1.toBuilder()
            .active(true)
            .measureSet(MeasureSet.builder().owner(username).build())
            .measureMetaData(draftMeasureMetaData)
            .build();

    // When
    when(measureService.findMeasureById(existingMeasure.getId())).thenReturn(existingMeasure);
    when(measureRepository.save(any(Measure.class))).thenReturn(existingMeasure);
    when(actionLogService.logAction(
            existingMeasure.getId(), Measure.class, ActionType.DELETED, username))
        .thenReturn(true);
    when(measureLockService.unlockMeasure(anyString(), anyString()))
        .thenReturn(LockInfo.builder().build());

    // Then
    Measure result = measureService.deactivateMeasure(existingMeasure.getId(), username);

    assertThat(result, is(notNullValue()));
    assertThat(result.isActive(), is(false));

    verify(measureRepository).save(measureArgumentCaptor.capture());
    Measure savedMeasure = measureArgumentCaptor.getValue();
    assertThat(savedMeasure.isActive(), is(false));
  }

  @Test
  public void testDeactivateCompositeMeasureDelegatesToSyncComponents() {
    // Given
    String username = "test-user";
    String componentMeasureId = "component-measure-id";

    List<Component> components = List.of(Component.builder().measureId(componentMeasureId).build());
    Group compositeGroup =
        Group.builder()
            .id("composite-group-id")
            .scoring(MeasureScoring.COMPOSITE.toString())
            .components(components)
            .build();

    MeasureMetaData compositeMeasureMetaData =
        draftMeasureMetaData.toBuilder().composite(true).build();

    Measure existingMeasure =
        measure1.toBuilder()
            .active(true)
            .measureSet(MeasureSet.builder().owner(username).build())
            .measureMetaData(compositeMeasureMetaData)
            .groups(new ArrayList<>(List.of(compositeGroup)))
            .build();

    // When
    when(measureService.findMeasureById(existingMeasure.getId())).thenReturn(existingMeasure);
    when(measureRepository.save(any(Measure.class))).thenReturn(existingMeasure);
    when(actionLogService.logAction(
            existingMeasure.getId(), Measure.class, ActionType.DELETED, username))
        .thenReturn(true);
    when(measureLockService.unlockMeasure(anyString(), anyString()))
        .thenReturn(LockInfo.builder().build());

    // Then
    measureService.deactivateMeasure(existingMeasure.getId(), username);

    verify(compositeRelationshipService)
        .syncComponents(eq(components), eq(List.of()), eq(existingMeasure), eq(username));
  }

  @Test
  public void testDeactivateCompositeMeasureWithEmptyGroupsDoesNotDelegate() {
    // Given
    String username = "test-user";

    MeasureMetaData compositeMeasureMetaData =
        draftMeasureMetaData.toBuilder().composite(true).build();

    Measure existingMeasure =
        measure1.toBuilder()
            .active(true)
            .measureSet(MeasureSet.builder().owner(username).build())
            .measureMetaData(compositeMeasureMetaData)
            .groups(new ArrayList<>())
            .build();

    // When
    when(measureService.findMeasureById(existingMeasure.getId())).thenReturn(existingMeasure);
    when(measureRepository.save(any(Measure.class))).thenReturn(existingMeasure);
    when(actionLogService.logAction(
            existingMeasure.getId(), Measure.class, ActionType.DELETED, username))
        .thenReturn(true);
    when(measureLockService.unlockMeasure(anyString(), anyString()))
        .thenReturn(LockInfo.builder().build());

    // Then
    measureService.deactivateMeasure(existingMeasure.getId(), username);

    verify(compositeRelationshipService, times(0)).syncComponents(any(), any(), any(), anyString());
  }

  @Test
  public void testDeactivateCompositeMeasureWithNoComponentsDoesNotDelegate() {
    // Given
    String username = "test-user";

    Group compositeGroupNoComponents =
        Group.builder()
            .id("composite-group-id")
            .scoring(MeasureScoring.COMPOSITE.toString())
            .components(new ArrayList<>())
            .build();

    MeasureMetaData compositeMeasureMetaData =
        draftMeasureMetaData.toBuilder().composite(true).build();

    Measure existingMeasure =
        measure1.toBuilder()
            .active(true)
            .measureSet(MeasureSet.builder().owner(username).build())
            .measureMetaData(compositeMeasureMetaData)
            .groups(new ArrayList<>(List.of(compositeGroupNoComponents)))
            .build();

    // When
    when(measureService.findMeasureById(existingMeasure.getId())).thenReturn(existingMeasure);
    when(measureRepository.save(any(Measure.class))).thenReturn(existingMeasure);
    when(actionLogService.logAction(
            existingMeasure.getId(), Measure.class, ActionType.DELETED, username))
        .thenReturn(true);
    when(measureLockService.unlockMeasure(anyString(), anyString()))
        .thenReturn(LockInfo.builder().build());

    // Then
    measureService.deactivateMeasure(existingMeasure.getId(), username);

    verify(compositeRelationshipService, times(0)).syncComponents(any(), any(), any(), anyString());
  }

  @Test
  public void testDeactivateNonCompositeMeasureDoesNotDelegate() {
    // Given
    String username = "test-user";

    Measure existingMeasure =
        measure1.toBuilder()
            .active(true)
            .measureSet(MeasureSet.builder().owner(username).build())
            .measureMetaData(draftMeasureMetaData)
            .build();

    // When
    when(measureService.findMeasureById(existingMeasure.getId())).thenReturn(existingMeasure);
    when(measureRepository.save(any(Measure.class))).thenReturn(existingMeasure);
    when(actionLogService.logAction(
            existingMeasure.getId(), Measure.class, ActionType.DELETED, username))
        .thenReturn(true);
    when(measureLockService.unlockMeasure(anyString(), anyString()))
        .thenReturn(LockInfo.builder().build());

    // Then
    measureService.deactivateMeasure(existingMeasure.getId(), username);

    verify(compositeRelationshipService, times(0)).syncComponents(any(), any(), any(), anyString());
  }

  @Test
  public void testGetMeasureLockLockedByOtherUser() {
    when(measureLockService.findByMeasureId(anyString()))
        .thenReturn(MeasureLock.builder().id("testMeasureId").lockedBy("testUserName2").build());

    gov.cms.madie.models.measure.MeasureLock measureLock =
        measureService.getMeasureLock("testMeasureId", "testUserName");

    assertNotNull(measureLock);
    assertEquals("testUserName2", measureLock.getLockedBy());
  }

  @Test
  public void testGetMeasureLockLockedBySelf() {
    when(measureLockService.findByMeasureId(anyString()))
        .thenReturn(MeasureLock.builder().id("testMeasureId").lockedBy("testUserName").build());

    gov.cms.madie.models.measure.MeasureLock measureLock =
        measureService.getMeasureLock("testMeasureId", "testUserName");

    assertNull(measureLock);
  }

  @Test
  public void testGetMeasureLockLockNotFound() {
    when(measureLockService.findByMeasureId(anyString())).thenReturn(null);

    gov.cms.madie.models.measure.MeasureLock measureLock =
        measureService.getMeasureLock("testMeasureId", "testUserName");

    assertNull(measureLock);
  }

  @Test
  public void testGetMeasureLockFeatureFlagNotEnabled() {

    gov.cms.madie.models.measure.MeasureLock measureLock =
        measureService.getMeasureLock("testMeasureId", "testUserName");

    assertNull(measureLock);
  }

  @Test
  void validateCodeSuffixes() {
    String cql =
        "code \"Therapy Appropriate (1234)\": '1' from \"ActCode\" display 'Therapy Appropriate'";
    assertDoesNotThrow(() -> measureService.validateCodeSuffixes(new CqlTextParser(cql), "mId"));

    String cqlWithInvalidCodeSuffix =
        "code \"Therapy Appropriate (12345)\": '1' from \"ActCode\" display 'Therapy Appropriate'";
    InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class,
            () ->
                measureService.validateCodeSuffixes(
                    new CqlTextParser(cqlWithInvalidCodeSuffix), "mId"));
    assertEquals(
        "Code suffixes must be 4 characters or less. Please correct the code: Therapy Appropriate (12345) with suffix: 12345",
        exception.getMessage());
  }

  @Test
  public void testUpdateMeasureThrowsExceptionForNullCql() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("libraryName")
            .measureName("Measure1")
            .cql("Some CQL Content")
            .active(true)
            .build();

    Measure updated = original.toBuilder().cql(null).active(true).build();

    when(measureUtil.isCqlLibraryNameChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(false);

    assertThrows(
        InvalidRequestException.class,
        () -> measureService.updateMeasure(original, "User1", updated, "Access Token"));
  }

  @Test
  public void testUpdateMeasureThrowsExceptionForWhenCqlIsNull() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .versionId("VersionId")
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .build();

    Measure updated = original.toBuilder().cql(null).build();
    when(measureUtil.isCqlLibraryNameChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(false);

    assertThrows(
        InvalidRequestException.class,
        () -> measureService.updateMeasure(original, "User1", updated, "Access Token"));
  }

  @Test
  void testGetMeasuresByIds() {
    List<String> inputIds = Arrays.asList("m1", "m2", "m3");

    MeasureSet measureSet1 = MeasureSet.builder().id("set1").owner("owner1").build();
    MeasureSet measureSet2 = MeasureSet.builder().id("set2").owner("owner2").build();
    MeasureSet measureSet3 = MeasureSet.builder().id("set3").owner("owner3").build();

    List<MeasureListDTO> repoResponse =
        List.of(
            MeasureListDTO.builder().id("m1").measureName("Alpha").measureSet(measureSet1).build(),
            MeasureListDTO.builder().id("m2").measureName("Beta").measureSet(measureSet2).build(),
            MeasureListDTO.builder().id("m3").measureName("Gamma").measureSet(measureSet3).build());

    when(measureRepository.findAllByIdInWithMeasureSet(List.of("m1", "m2", "m3")))
        .thenReturn(repoResponse);

    List<MeasureListDTO> result = measureService.getMeasuresByIds(inputIds);

    assertNotNull(result);
    assertEquals(3, result.size());
    assertEquals("m1", result.get(0).getId());
    assertEquals("m2", result.get(1).getId());
    assertEquals("m3", result.get(2).getId());
    // Verify measureSet is populated
    assertNotNull(result.get(0).getMeasureSet());
    assertNotNull(result.get(1).getMeasureSet());
    assertNotNull(result.get(2).getMeasureSet());

    verify(measureRepository, times(1)).findAllByIdInWithMeasureSet(List.of("m1", "m2", "m3"));
    verifyNoMoreInteractions(measureRepository);
  }
}
