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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import gov.cms.madie.models.measure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import cms.gov.madie.measure.dto.MadieFeatureFlag;
import cms.gov.madie.measure.exceptions.CqlElmTranslationErrorException;
import cms.gov.madie.measure.exceptions.CqlElmTranslationServiceException;
import cms.gov.madie.measure.exceptions.DuplicateMeasureException;
import cms.gov.madie.measure.exceptions.InvalidDeletionCredentialsException;
import cms.gov.madie.measure.exceptions.InvalidMeasureStateException;
import cms.gov.madie.measure.exceptions.InvalidMeasurementPeriodException;
import cms.gov.madie.measure.exceptions.InvalidTerminologyException;
import cms.gov.madie.measure.exceptions.InvalidVersionIdException;
import cms.gov.madie.measure.exceptions.UnauthorizedException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.OrganizationRepository;
import cms.gov.madie.measure.resources.DuplicateKeyException;
import cms.gov.madie.measure.utils.MeasureUtil;
import cms.gov.madie.measure.utils.ResourceUtil;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.common.Version;

@ExtendWith(MockitoExtension.class)
public class MeasureServiceTest implements ResourceUtil {
  @Mock private MeasureRepository measureRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private ElmTranslatorClient elmTranslatorClient;
  @Mock private MeasureUtil measureUtil;
  @Mock private ActionLogService actionLogService;
  @Mock private MeasureSetService measureSetService;
  @Mock private TerminologyValidationService terminologyValidationService;
  @Mock private AppConfigService appConfigService;
  @Mock private MeasureTransferService measureTransferService;

  @InjectMocks private MeasureService measureService;

  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;

  private Group group2;
  private MeasureMetaData draftMeasureMetaData;
  private MeasureMetaData finalMeasureMetaData;
  private String elmJson;
  private Measure measure1;
  private Measure measure2;
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
            .riskAdjustment("test risk adjustment")
            .definition("test definition")
            .experimental(false)
            .transmissionFormat("test transmission format")
            .supplementalDataElements("test supplemental data elements")
            .build();

    finalMeasureMetaData =
        MeasureMetaData.builder()
            .steward(Organization.builder().name("SB").build())
            .developers(developersList)
            .copyright("Copyright@SB")
            .references(references)
            .draft(false)
            .endorsements(endorsements)
            .riskAdjustment("test risk adjustment")
            .definition("test definition")
            .experimental(false)
            .transmissionFormat("test transmission format")
            .supplementalDataElements("test supplemental data elements")
            .build();

    // Present in DB and has ID
    group2 =
        Group.builder()
            .id("xyz-p12r-12ert")
            .populationBasis("Encounter")
            .populations(
                List.of(
                    new Population(
                        "id-1", PopulationType.INITIAL_POPULATION, "FactorialOfFive", null, null)))
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
            .active(true)
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
            .elmJson(elmJson)
            .active(true)
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

    organizationList = new ArrayList<>();
    organizationList.add(Organization.builder().name("SB").url("SB Url").build());
    organizationList.add(Organization.builder().name("SB 2").url("SB 2 Url").build());
    organizationList.add(Organization.builder().name("CancerLinQ").url("CancerLinQ Url").build());
    organizationList.add(Organization.builder().name("Innovaccer").url("Innovaccer Url").build());
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
    acl1.setRoles(List.of(RoleEnum.SHARED_WITH));
    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("User2");
    acl2.setRoles(List.of(RoleEnum.SHARED_WITH));
    MeasureSet measureSet = MeasureSet.builder().owner("OWNER").acls(List.of(acl1, acl2)).build();
    Measure measure = Measure.builder().measureSet(measureSet).build();
    assertThrows(
        UnauthorizedException.class, () -> measureService.verifyAuthorization("THEUSER", measure));
  }

  @Test
  public void testVerifyAuthorizationDoesNothingForNotInAclsAndOwner() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("User1");
    acl1.setRoles(List.of(RoleEnum.SHARED_WITH));
    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("User2");
    acl2.setRoles(List.of(RoleEnum.SHARED_WITH));
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
    acl1.setRoles(List.of(RoleEnum.SHARED_WITH));
    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("THEUSER");
    acl2.setRoles(List.of(RoleEnum.SHARED_WITH));
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
  public void testGetMeasuresByCriteriaWithCurrentUser() {
    PageRequest initialPage = PageRequest.of(0, 10);

    Page<Measure> activeMeasures = new PageImpl<>(List.of(measure1));

    doReturn(activeMeasures)
        .when(measureRepository)
        .findMyActiveMeasures(eq("test.user"), any(PageRequest.class), eq("test criteria"));
    Object measures =
        measureService.getMeasuresByCriteria(true, initialPage, "test.user", "test criteria");
    assertNotNull(measures);
  }

  @Test
  public void testGetMeasuresByCriteriaWithoutCurrentUser() {
    PageRequest initialPage = PageRequest.of(0, 10);

    Page<Measure> activeMeasures = new PageImpl<>(List.of(measure1));

    doReturn(activeMeasures)
        .when(measureRepository)
        .findAllByMeasureNameOrEcqmTitle(eq("test criteria"), any(PageRequest.class));
    Object measures =
        measureService.getMeasuresByCriteria(false, initialPage, "test.user", "test criteria");
    assertNotNull(measures);
  }

  @Test
  public void testGetMeasures() {
    PageRequest initialPage = PageRequest.of(0, 10);

    Page<Measure> activeMeasures = new PageImpl<>(List.of(measure1));

    doReturn(activeMeasures)
        .when(measureRepository)
        .findAllByActive(eq(true), any(PageRequest.class));
    Object measures = measureService.getMeasures(false, initialPage, null);
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
  public void testCreateMeasureSuccessfullyWithNoCql() {
    String usr = "john rao";
    Measure measureToSave =
        measure1.toBuilder()
            .measurementPeriodStart(Date.from(Instant.now().minus(38, ChronoUnit.DAYS)))
            .measurementPeriodEnd(Date.from(Instant.now().minus(11, ChronoUnit.DAYS)))
            .measureSetId("msid-1")
            .cqlLibraryName("VTE")
            .cql("")
            .elmJson(null)
            .measureMetaData(new MeasureMetaData())
            .createdBy(usr)
            .build();
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);

    Measure savedMeasure = measureService.createMeasure(measureToSave, usr, "token");
    assertThat(savedMeasure.getMeasureName(), is(equalTo(measureToSave.getMeasureName())));
    assertThat(savedMeasure.getCqlLibraryName(), is(equalTo(measureToSave.getCqlLibraryName())));
    assertThat(savedMeasure.getCreatedBy(), is(equalTo(usr)));
    assertThat(savedMeasure.isCqlErrors(), is(equalTo(false)));
    assertThat(savedMeasure.getErrors(), is(emptySet()));
    assertThat(savedMeasure.getMeasureMetaData().isDraft(), is(equalTo(true)));
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

    when(measureRepository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json(elmJson).build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);
    doNothing().when(terminologyValidationService).validateTerminology(anyString(), anyString());
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);

    Measure savedMeasure = measureService.createMeasure(measureToSave, "john rao", "token");
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

    when(measureRepository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
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

    Measure savedMeasure = measureService.createMeasure(measureToSave, usr, "token");
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
            .cqlLibraryName("VTE")
            .cql("")
            .elmJson(null)
            .build();
    when(measureRepository.findByCqlLibraryName(anyString())).thenReturn(Optional.of(measure1));

    assertThrows(
        DuplicateKeyException.class,
        () -> measureService.createMeasure(measureToSave, "john rao", "token"),
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

    when(measureRepository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json(elmJson).build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);
    doNothing().when(terminologyValidationService).validateTerminology(anyString(), anyString());
    doNothing()
        .when(measureSetService)
        .createMeasureSet(anyString(), anyString(), anyString(), any());
    when(measureRepository.save(any(Measure.class))).thenReturn(measureToSave);
    when(actionLogService.logAction(any(), any(), any(), any())).thenReturn(true);

    Measure savedMeasure = measureService.createMeasure(measureToSave, "john rao", "token");
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
  public void testUpdateMeasureThrowsExceptionForDuplicateLibraryName() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .cmsId("CMS_ID1")
            .build();

    Measure updated = original.toBuilder().cqlLibraryName("Changed_Name").build();

    when(measureUtil.isCqlLibraryNameChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(true);
    when(measureRepository.findByCqlLibraryName(anyString()))
        .thenReturn(Optional.of(Measure.builder().build()));

    assertThrows(
        DuplicateKeyException.class,
        () -> measureService.updateMeasure(original, "User1", updated, "Access Token"));
  }

  @Test
  public void testUpdateMeasureThrowsExceptionForInvalidMeasurementPeriod() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .cmsId("CMS_ID1")
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
            .cmsId("CMS_ID1")
            .measureSetId("MeasureSetId")
            .cqlLibraryName("CqlLibraryName")
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
    when(measureRepository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
    when(measureUtil.isMeasurementPeriodChanged(any(Measure.class), any(Measure.class)))
        .thenReturn(true);
    when(measureUtil.isMeasureCqlChanged(any(Measure.class), any(Measure.class))).thenReturn(false);
    when(measureRepository.save(any(Measure.class))).thenReturn(updated);

    Measure output = measureService.updateMeasure(original, "User1", updated, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(updated)));

    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
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
            .cmsId("CMS_ID1")
            .versionId("VersionId")
            .cql("original cql here")
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
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);

    Measure expected =
        updated.toBuilder().error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES).build();
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class))).thenReturn(expected);
    when(measureRepository.save(any(Measure.class))).thenReturn(expected);

    Measure output = measureService.updateMeasure(original, "User1", updated, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(expected)));

    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure persisted = measureArgumentCaptor.getValue();
    assertThat(persisted, is(equalTo(expected)));
  }

  @Test
  public void testUpdateMeasureSavesMeasureWithUpdatedCqlAndErrors() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .cmsId("CMS_ID1")
            .versionId("VersionId")
            .cql("original cql here")
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
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);

    Measure expected =
        updated.toBuilder()
            .cqlErrors(true)
            .error(MeasureErrorType.MISMATCH_CQL_POPULATION_RETURN_TYPES)
            .build();
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class))).thenReturn(expected);
    when(measureRepository.save(any(Measure.class))).thenReturn(expected);

    Measure output = measureService.updateMeasure(original, "User1", updated, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(expected)));

    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure persisted = measureArgumentCaptor.getValue();
    assertThat(persisted, is(equalTo(expected)));
  }

  @Test
  public void testUpdateMeasureSavesMeasureWithUpdatedCqlAndErrorsGettingElm() {
    Measure original =
        Measure.builder()
            .cqlLibraryName("OriginalLibName")
            .measureName("Measure1")
            .cmsId("CMS_ID1")
            .versionId("VersionId")
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
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(true);

    when(measureRepository.save(any(Measure.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

    Measure output = measureService.updateMeasure(original, "User1", updated, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output.getErrors(), is(notNullValue()));
    assertThat(output.isCqlErrors(), is(true));
    assertThat(output.getErrors().contains(MeasureErrorType.ERRORS_ELM_JSON), is(true));

    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
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
    final Measure measure = Measure.builder().cql("some really good cql here").build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(true);
    assertThrows(
        CqlElmTranslationErrorException.class,
        () -> measureService.updateElm(measure, "Access Token"));
  }

  @Test
  public void testUpdateElmReturnsElmJson() {
    final Measure measure = Measure.builder().cql("some really good cql here").build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library></library>").build());
    Measure output = measureService.updateElm(measure, "Access Token");
    assertThat(output, is(notNullValue()));
    assertThat(output.getElmJson(), is(equalTo("{\"library\": {}}")));
    assertThat(output.getElmXml(), is(equalTo("<library></library>")));
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
    Page<Measure> activeMeasures = new PageImpl<>(List.of(measure1, m1));
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
  public void testValidateMeasureMeasurementPeriodWithNullStartDate() {
    LocalDate endDate = LocalDate.parse("2022-12-31");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            measureService.validateMeasurementPeriod(
                null, Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant())));
  }

  @Test
  public void testValidateMeasureMeasurementPeriodWithNullEndDate() {
    LocalDate startDate = LocalDate.parse("2022-01-01");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            measureService.validateMeasurementPeriod(
                Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
                null));
  }

  @Test
  public void testValidateMeasureMeasurementPeriodTooEarlyDate() {
    LocalDate startDate = LocalDate.parse("0001-01-01");
    LocalDate endDate = LocalDate.parse("2022-12-31");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            measureService.validateMeasurementPeriod(
                Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
                Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant())));
  }

  @Test
  public void testValidateMeasureMeasurementPeriodFlippedDates() {
    LocalDate startDate = LocalDate.parse("2022-01-01");
    LocalDate endDate = LocalDate.parse("2022-12-31");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            measureService.validateMeasurementPeriod(
                Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
                Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant())));
  }

  @Test
  public void testValidateMeasureMeasurementPeriodEndDateEqualStartDate() {
    LocalDate startDate = LocalDate.parse("2022-12-31");
    LocalDate endDate = LocalDate.parse("2022-12-31");

    assertThrows(
        InvalidMeasurementPeriodException.class,
        () ->
            measureService.validateMeasurementPeriod(
                Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
                Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant())));
  }

  @Test
  public void testValidateMeasureMeasurementPeriod() {
    try {
      LocalDate startDate = LocalDate.parse("2022-01-01");
      LocalDate endDate = LocalDate.parse("2023-01-01");

      measureService.validateMeasurementPeriod(
          Date.from(startDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
          Date.from(endDate.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()));

    } catch (Exception e) {
      fail(e.getMessage());
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

  @Test
  public void testInvalidVersionIdThrowsExceptionForDifferentVersionIds() {
    assertThrows(
        InvalidVersionIdException.class,
        () -> measureService.checkVersionIdChanged("versionId1", "versionId2"));
  }

  @Test
  public void testInvalidVersionThrowsExceptionWhenPassedInVersionIsNull() {
    assertThrows(
        InvalidVersionIdException.class,
        () -> measureService.checkVersionIdChanged("", "versionId1"));
  }

  @Test
  public void testInvalidVersionIdDoesNotThrowExceptionWhenMatch() {
    try {
      measureService.checkVersionIdChanged("versionId1", "versionId1");
    } catch (Exception e) {
      fail("Should not throw unexpected exception");
    }
  }

  @Test
  public void testInvalidVersionIdDoesNotThrowExceptionWhenBothAreNull() {
    try {
      measureService.checkVersionIdChanged(null, null);
    } catch (Exception e) {
      fail("Should not throw unexpected exception");
    }
  }

  @Test
  public void testInvalidVersionIdDoesNotThrowExceptionWhenVersionIdFromDBIsNull() {
    try {
      measureService.checkVersionIdChanged("versionId1", null);
    } catch (Exception e) {
      fail("Should not throw unexpected exception");
    }
  }

  @Test
  public void testChangeOwnership() {
    MeasureSet measureSet = MeasureSet.builder().measureSetId("123").owner("testUser").build();
    Measure measure =
        Measure.builder().id("123").measureSetId("123").measureSet(measureSet).build();
    Optional<Measure> persistedMeasure = Optional.of(measure);
    when(measureRepository.findById(anyString())).thenReturn(persistedMeasure);
    when(measureSetService.updateOwnership(anyString(), anyString())).thenReturn(new MeasureSet());

    boolean result = measureService.changeOwnership(measure.getId(), "user123");
    assertTrue(result);
  }

  @Test
  public void testGrantAccessNoMeasure() {
    Optional<Measure> persistedMeasure = Optional.empty();
    when(measureRepository.findById(eq("123"))).thenReturn(persistedMeasure);
    boolean result = measureService.grantAccess("123", "user123");

    assertFalse(result);
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
  public void testUpdateReferenceIdNullMetaData() {
    MeasureMetaData metaData = null;
    measureService.updateReferenceId(metaData);
    assertNull(metaData);
  }

  @Test
  public void testUpdateReferenceIdNullReferences() {
    MeasureMetaData metaData = MeasureMetaData.builder().build();
    measureService.updateReferenceId(metaData);
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

  //// New Tests after refactoring

  @Test
  void testImportMeasureSuccessQiCoreAndNewLibrary() {
    doReturn(measure1).when(measureRepository).save(any(Measure.class));
    when(organizationRepository.findAll()).thenReturn(organizationList);

    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");

    assertNotNull(persistedMeasure);

    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure1.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure1.getModel(), ModelType.QI_CORE.getValue());

    assertEquals(measure1.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure1.getCql(), persistedMeasure.getCql());
    assertEquals(measure1.getGroups().size(), persistedMeasure.getGroups().size());
    assertEquals(
        measure1.getGroups().get(0).getPopulations().get(0).getDescription(),
        persistedMeasure.getGroups().get(0).getPopulations().get(0).getDescription());
    assertEquals(
        measure1.getMeasureMetaData().getReferences().get(0).getReferenceText(),
        persistedMeasure.getMeasureMetaData().getReferences().get(0).getReferenceText());
    assertEquals(
        measure1.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        persistedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser());
    assertEquals(
        measure1.getMeasureMetaData().isDraft(), persistedMeasure.getMeasureMetaData().isDraft());
    assertEquals(
        measure1.getMeasureMetaData().getRiskAdjustment(),
        persistedMeasure.getMeasureMetaData().getRiskAdjustment());
    assertEquals(
        measure1.getMeasureMetaData().getDefinition(),
        persistedMeasure.getMeasureMetaData().getDefinition());
    assertEquals(
        measure1.getMeasureMetaData().isExperimental(),
        persistedMeasure.getMeasureMetaData().isExperimental());
    assertEquals(
        measure1.getMeasureMetaData().getTransmissionFormat(),
        persistedMeasure.getMeasureMetaData().getTransmissionFormat());
    assertEquals(
        measure1.getMeasureMetaData().getSupplementalDataElements(),
        persistedMeasure.getMeasureMetaData().getSupplementalDataElements());

    assertEquals("SB Url", persistedMeasure.getMeasureMetaData().getSteward().getUrl());
    assertEquals(1, persistedMeasure.getMeasureMetaData().getDevelopers().size());
    assertEquals("SB 2 Url", persistedMeasure.getMeasureMetaData().getDevelopers().get(0).getUrl());
  }

  @Test
  void testImportMeasureSuccessNullDevelopersList() {
    measure1.getMeasureMetaData().setDevelopers(null);
    doReturn(measure1).when(measureRepository).save(any(Measure.class));

    when(organizationRepository.findAll()).thenReturn(organizationList);

    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");

    assertNotNull(persistedMeasure);

    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure1.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure1.getModel(), ModelType.QI_CORE.getValue());

    assertEquals(measure1.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure1.getCql(), persistedMeasure.getCql());
    assertEquals(measure1.getGroups().size(), persistedMeasure.getGroups().size());
    assertEquals(
        measure1.getGroups().get(0).getPopulations().get(0).getDescription(),
        persistedMeasure.getGroups().get(0).getPopulations().get(0).getDescription());
    assertEquals(
        measure1.getMeasureMetaData().getReferences().get(0).getReferenceText(),
        persistedMeasure.getMeasureMetaData().getReferences().get(0).getReferenceText());
    assertEquals(
        measure1.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        persistedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser());
    assertEquals(
        measure1.getMeasureMetaData().isDraft(), persistedMeasure.getMeasureMetaData().isDraft());
    assertEquals(
        measure1.getMeasureMetaData().getRiskAdjustment(),
        persistedMeasure.getMeasureMetaData().getRiskAdjustment());
    assertEquals(
        measure1.getMeasureMetaData().getDefinition(),
        persistedMeasure.getMeasureMetaData().getDefinition());
    assertEquals(
        measure1.getMeasureMetaData().isExperimental(),
        persistedMeasure.getMeasureMetaData().isExperimental());
    assertEquals(
        measure1.getMeasureMetaData().getTransmissionFormat(),
        persistedMeasure.getMeasureMetaData().getTransmissionFormat());
    assertEquals(
        measure1.getMeasureMetaData().getSupplementalDataElements(),
        persistedMeasure.getMeasureMetaData().getSupplementalDataElements());

    assertEquals("SB Url", persistedMeasure.getMeasureMetaData().getSteward().getUrl());
    assertNull(persistedMeasure.getMeasureMetaData().getDevelopers());
  }

  @Test
  void testImportMeasureSuccessEmptyDevelopersList() {
    measure1.getMeasureMetaData().setDevelopers(new ArrayList<>());
    doReturn(measure1).when(measureRepository).save(any(Measure.class));

    when(organizationRepository.findAll()).thenReturn(organizationList);

    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");

    assertNotNull(persistedMeasure);

    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure1.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure1.getModel(), ModelType.QI_CORE.getValue());

    assertEquals(measure1.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure1.getCql(), persistedMeasure.getCql());
    assertEquals(measure1.getGroups().size(), persistedMeasure.getGroups().size());
    assertEquals(
        measure1.getGroups().get(0).getPopulations().get(0).getDescription(),
        persistedMeasure.getGroups().get(0).getPopulations().get(0).getDescription());
    assertEquals(
        measure1.getMeasureMetaData().getReferences().get(0).getReferenceText(),
        persistedMeasure.getMeasureMetaData().getReferences().get(0).getReferenceText());
    assertEquals(
        measure1.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        persistedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser());
    assertEquals(
        measure1.getMeasureMetaData().isDraft(), persistedMeasure.getMeasureMetaData().isDraft());
    assertEquals(
        measure1.getMeasureMetaData().getRiskAdjustment(),
        persistedMeasure.getMeasureMetaData().getRiskAdjustment());
    assertEquals(
        measure1.getMeasureMetaData().getDefinition(),
        persistedMeasure.getMeasureMetaData().getDefinition());
    assertEquals(
        measure1.getMeasureMetaData().isExperimental(),
        persistedMeasure.getMeasureMetaData().isExperimental());
    assertEquals(
        measure1.getMeasureMetaData().getTransmissionFormat(),
        persistedMeasure.getMeasureMetaData().getTransmissionFormat());
    assertEquals(
        measure1.getMeasureMetaData().getSupplementalDataElements(),
        persistedMeasure.getMeasureMetaData().getSupplementalDataElements());

    assertEquals("SB Url", persistedMeasure.getMeasureMetaData().getSteward().getUrl());
    assertEquals(0, persistedMeasure.getMeasureMetaData().getDevelopers().size());
  }

  @Test
  void testImportMeasureSuccessMatchMeasureSteward() {
    doReturn(measure1).when(measureRepository).save(any(Measure.class));
    when(organizationRepository.findAll()).thenReturn(organizationList);
    measure1
        .getMeasureMetaData()
        .setSteward(Organization.builder().name("Innovaccer Anylytics").build());

    measure1
        .getMeasureMetaData()
        .getDevelopers()
        .add(Organization.builder().name("Innovaccer Anylytics").build());
    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");

    assertNotNull(persistedMeasure);

    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure1.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure1.getModel(), ModelType.QI_CORE.getValue());

    assertEquals(measure1.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure1.getCql(), persistedMeasure.getCql());
    assertEquals(measure1.getGroups().size(), persistedMeasure.getGroups().size());
    assertEquals(
        measure1.getGroups().get(0).getPopulations().get(0).getDescription(),
        persistedMeasure.getGroups().get(0).getPopulations().get(0).getDescription());
    assertEquals(
        measure1.getMeasureMetaData().getReferences().get(0).getReferenceText(),
        persistedMeasure.getMeasureMetaData().getReferences().get(0).getReferenceText());
    assertEquals(
        measure1.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        persistedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser());
    assertEquals(
        measure1.getMeasureMetaData().isDraft(), persistedMeasure.getMeasureMetaData().isDraft());
    assertEquals(
        measure1.getMeasureMetaData().getRiskAdjustment(),
        persistedMeasure.getMeasureMetaData().getRiskAdjustment());
    assertEquals(
        measure1.getMeasureMetaData().getDefinition(),
        persistedMeasure.getMeasureMetaData().getDefinition());
    assertEquals(
        measure1.getMeasureMetaData().isExperimental(),
        persistedMeasure.getMeasureMetaData().isExperimental());
    assertEquals(
        measure1.getMeasureMetaData().getTransmissionFormat(),
        persistedMeasure.getMeasureMetaData().getTransmissionFormat());
    assertEquals(
        measure1.getMeasureMetaData().getSupplementalDataElements(),
        persistedMeasure.getMeasureMetaData().getSupplementalDataElements());

    assertEquals("Innovaccer Url", persistedMeasure.getMeasureMetaData().getSteward().getUrl());
    assertEquals(2, persistedMeasure.getMeasureMetaData().getDevelopers().size());
    assertEquals("SB 2 Url", persistedMeasure.getMeasureMetaData().getDevelopers().get(0).getUrl());
    assertEquals(
        "Innovaccer Url", persistedMeasure.getMeasureMetaData().getDevelopers().get(1).getUrl());
  }

  @Test
  void testImportMeasureSuccessElmJsonErrorsAndEnableRepeatIsTrueAndQiCoreAndNewLibrary() {
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    doReturn(measure1).when(measureRepository).save(any(Measure.class));

    when(elmTranslatorClient.getElmJsonForMatMeasure(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json(elmJson).build());

    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(true);

    when(organizationRepository.findAll()).thenReturn(organizationList);

    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");

    assertNotNull(persistedMeasure);

    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure1.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure1.getModel(), ModelType.QI_CORE.getValue());

    assertEquals(measure1.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure1.getCql(), persistedMeasure.getCql());
    assertEquals(measure1.getGroups().size(), persistedMeasure.getGroups().size());
    assertEquals(
        measure1.getGroups().get(0).getPopulations().get(0).getDescription(),
        persistedMeasure.getGroups().get(0).getPopulations().get(0).getDescription());
    assertEquals(
        measure1.getMeasureMetaData().getReferences().get(0).getReferenceText(),
        persistedMeasure.getMeasureMetaData().getReferences().get(0).getReferenceText());
    assertEquals(
        measure1.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        persistedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser());
    assertEquals(
        measure1.getMeasureMetaData().isDraft(), persistedMeasure.getMeasureMetaData().isDraft());
    assertEquals(
        measure1.getMeasureMetaData().getRiskAdjustment(),
        persistedMeasure.getMeasureMetaData().getRiskAdjustment());
    assertEquals(
        measure1.getMeasureMetaData().getDefinition(),
        persistedMeasure.getMeasureMetaData().getDefinition());
    assertEquals(
        measure1.getMeasureMetaData().isExperimental(),
        persistedMeasure.getMeasureMetaData().isExperimental());
    assertEquals(
        measure1.getMeasureMetaData().getTransmissionFormat(),
        persistedMeasure.getMeasureMetaData().getTransmissionFormat());
    assertEquals(
        measure1.getMeasureMetaData().getSupplementalDataElements(),
        persistedMeasure.getMeasureMetaData().getSupplementalDataElements());

    assertEquals("SB Url", persistedMeasure.getMeasureMetaData().getSteward().getUrl());
    assertEquals(1, persistedMeasure.getMeasureMetaData().getDevelopers().size());
    assertEquals("SB 2 Url", persistedMeasure.getMeasureMetaData().getDevelopers().get(0).getUrl());
  }

  @Test
  void testImportMeasureSuccessElmJsonErrorsThrowsError() {
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    doReturn(measure1).when(measureRepository).save(any(Measure.class));

    doThrow(new CqlElmTranslationServiceException(elmJson, null))
        .when(elmTranslatorClient)
        .getElmJsonForMatMeasure(anyString(), anyString(), anyString());

    when(organizationRepository.findAll()).thenReturn(organizationList);

    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");

    assertNotNull(persistedMeasure);

    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure1.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure1.getModel(), ModelType.QI_CORE.getValue());

    assertEquals(measure1.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure1.getCql(), persistedMeasure.getCql());
    assertEquals(measure1.getGroups().size(), persistedMeasure.getGroups().size());
    assertEquals(
        measure1.getGroups().get(0).getPopulations().get(0).getDescription(),
        persistedMeasure.getGroups().get(0).getPopulations().get(0).getDescription());
    assertEquals(
        measure1.getMeasureMetaData().getReferences().get(0).getReferenceText(),
        persistedMeasure.getMeasureMetaData().getReferences().get(0).getReferenceText());
    assertEquals(
        measure1.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        persistedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser());
    assertEquals(
        measure1.getMeasureMetaData().isDraft(), persistedMeasure.getMeasureMetaData().isDraft());
    assertEquals(
        measure1.getMeasureMetaData().getRiskAdjustment(),
        persistedMeasure.getMeasureMetaData().getRiskAdjustment());
    assertEquals(
        measure1.getMeasureMetaData().getDefinition(),
        persistedMeasure.getMeasureMetaData().getDefinition());
    assertEquals(
        measure1.getMeasureMetaData().isExperimental(),
        persistedMeasure.getMeasureMetaData().isExperimental());
    assertEquals(
        measure1.getMeasureMetaData().getTransmissionFormat(),
        persistedMeasure.getMeasureMetaData().getTransmissionFormat());
    assertEquals(
        measure1.getMeasureMetaData().getSupplementalDataElements(),
        persistedMeasure.getMeasureMetaData().getSupplementalDataElements());

    assertEquals("SB Url", persistedMeasure.getMeasureMetaData().getSteward().getUrl());
    assertEquals(1, persistedMeasure.getMeasureMetaData().getDevelopers().size());
    assertEquals("SB 2 Url", persistedMeasure.getMeasureMetaData().getDevelopers().get(0).getUrl());
  }

  @Test
  void testImportMeasureSuccessEnableRepeatIsTrueAndQiCoreAndNewLibrary() {
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    doReturn(measure1).when(measureRepository).save(any(Measure.class));
    when(organizationRepository.findAll()).thenReturn(organizationList);

    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");

    assertNotNull(persistedMeasure);

    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure1.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure1.getModel(), ModelType.QI_CORE.getValue());

    assertEquals(measure1.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure1.getCql(), persistedMeasure.getCql());
    assertEquals(measure1.getGroups().size(), persistedMeasure.getGroups().size());
    assertEquals(
        measure1.getGroups().get(0).getPopulations().get(0).getDescription(),
        persistedMeasure.getGroups().get(0).getPopulations().get(0).getDescription());
    assertEquals(
        measure1.getMeasureMetaData().getReferences().get(0).getReferenceText(),
        persistedMeasure.getMeasureMetaData().getReferences().get(0).getReferenceText());
    assertEquals(
        measure1.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        persistedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser());
    assertEquals(
        measure1.getMeasureMetaData().isDraft(), persistedMeasure.getMeasureMetaData().isDraft());
    assertEquals(
        measure1.getMeasureMetaData().getRiskAdjustment(),
        persistedMeasure.getMeasureMetaData().getRiskAdjustment());
    assertEquals(
        measure1.getMeasureMetaData().getDefinition(),
        persistedMeasure.getMeasureMetaData().getDefinition());
    assertEquals(
        measure1.getMeasureMetaData().isExperimental(),
        persistedMeasure.getMeasureMetaData().isExperimental());
    assertEquals(
        measure1.getMeasureMetaData().getTransmissionFormat(),
        persistedMeasure.getMeasureMetaData().getTransmissionFormat());
    assertEquals(
        measure1.getMeasureMetaData().getSupplementalDataElements(),
        persistedMeasure.getMeasureMetaData().getSupplementalDataElements());

    assertEquals("SB Url", persistedMeasure.getMeasureMetaData().getSteward().getUrl());
    assertEquals(1, persistedMeasure.getMeasureMetaData().getDevelopers().size());
    assertEquals("SB 2 Url", persistedMeasure.getMeasureMetaData().getDevelopers().get(0).getUrl());
  }

  @Test
  void testImportMeasureFailureEnableRepeatIsFalseAndWhenMeasureSetExists() {
    doReturn(false)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    doReturn(List.of(measure1, measure2))
        .when(measureRepository)
        .findAllByMeasureSetIdAndActive(eq("IDIDID"), eq(true));

    Exception exception =
        assertThrows(
            DuplicateMeasureException.class,
            () -> {
              measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
              ;
            });

    String expectedMessage = "The measure already exists";
    String actualMessage = exception.getMessage();

    assertTrue(actualMessage.contains(expectedMessage));

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
  }

  @Test
  void testImportMeasureFailureEnableRepeatIsTrueAndQICCoreAndWhenMeasureSetExists() {
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    doReturn(List.of(measure1, measure2))
        .when(measureRepository)
        .findAllByMeasureSetIdAndActive(eq("IDIDID"), eq(true));

    Exception exception =
        assertThrows(
            DuplicateMeasureException.class,
            () -> {
              measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
              ;
            });

    String expectedMessage = "The measure already exists";
    String actualMessage = exception.getMessage();

    assertTrue(actualMessage.contains(expectedMessage));

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
  }

  @Test
  void testImportMeasureFailureEnableRepeatFalseAndQiCoreAndDuplicateLibraryName() {
    doReturn(false)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    doReturn(Optional.of(measure2))
        .when(measureRepository)
        .findByCqlLibraryName(eq("MSR01Library"));

    Exception exception =
        assertThrows(
            DuplicateKeyException.class,
            () -> {
              measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
              ;
            });

    String expectedMessage = "CQL library with given name already exists.";
    String actualMessage = exception.getMessage();

    assertTrue(actualMessage.contains(expectedMessage));

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
  }

  @Test
  void testImportMeasureFailureEnableRepeatTrueAndQiCoreAndDuplicateLibraryName() {
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    doReturn(Optional.of(measure2))
        .when(measureRepository)
        .findByCqlLibraryName(eq("MSR01Library"));

    Exception exception =
        assertThrows(
            DuplicateKeyException.class,
            () -> {
              measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
              ;
            });

    String expectedMessage = "CQL library with given name already exists.";
    String actualMessage = exception.getMessage();

    assertTrue(actualMessage.contains(expectedMessage));

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
  }

  @Test
  void testImportMeasureFailureEnableRepeatTrueAndQdmAndDuplicateLibraryName() {
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    measure1.setModel(ModelType.QDM_5_6.getValue());

    doReturn(Optional.of(measure2))
        .when(measureRepository)
        .findByCqlLibraryName(eq("MSR01Library"));

    Exception exception =
        assertThrows(
            DuplicateKeyException.class,
            () -> {
              measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
              ;
            });

    String expectedMessage = "CQL library with given name already exists.";
    String actualMessage = exception.getMessage();

    assertTrue(actualMessage.contains(expectedMessage));

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
  }

  @Test
  void testImportMeasureSuccessEnableRepeatTrueAndQdmAndSameMeasureSetId() {
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));

    measure1.setModel(ModelType.QDM_5_6.getValue());

    measure2.setMeasureMetaData(finalMeasureMetaData);
    measure2.setMeasureSetId(measure1.getMeasureSetId());
    doReturn(List.of(measure2))
        .when(measureRepository)
        .findAllByMeasureSetIdAndActive(eq("IDIDID"), eq(true));
    doReturn(Optional.of(measure2))
        .when(measureRepository)
        .findByCqlLibraryName(eq("MSR01Library"));
    when(organizationRepository.findAll()).thenReturn(organizationList);
    doReturn(measure1).when(measureRepository).save(any(Measure.class));
    doReturn(measure1)
        .when(measureTransferService)
        .overwriteExistingMeasure(anyList(), eq(measure1));
    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
    verify(measureRepository, times(1)).save(any(Measure.class));
    verify(measureRepository, times(1)).deleteAll(anyList());
    verify(measureTransferService, times(1)).overwriteExistingMeasure(anyList(), eq(measure1));
  }

  @Test
  void testImportMeasureSuccessEnableRepeatTrueAndQdmAndSameMeasureSetIdAndNoMeasureData() {
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));

    measure1.setModel(ModelType.QDM_5_6.getValue());
    measure1.setMeasureMetaData(null);
    measure2.setMeasureMetaData(finalMeasureMetaData);
    measure2.setMeasureSetId(measure1.getMeasureSetId());
    doReturn(List.of(measure2))
        .when(measureRepository)
        .findAllByMeasureSetIdAndActive(eq("IDIDID"), eq(true));
    doReturn(Optional.of(measure2))
        .when(measureRepository)
        .findByCqlLibraryName(eq("MSR01Library"));
    when(organizationRepository.findAll()).thenReturn(organizationList);
    doReturn(measure1).when(measureRepository).save(any(Measure.class));
    doReturn(measure1)
        .when(measureTransferService)
        .overwriteExistingMeasure(anyList(), eq(measure1));
    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
    verify(measureRepository, times(1)).save(any(Measure.class));
    verify(measureRepository, times(1)).deleteAll(anyList());
    verify(measureTransferService, times(1)).overwriteExistingMeasure(anyList(), eq(measure1));
  }

  @Test
  void testImportMeasureSuccessMissingCqlLibraryName() {
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));

    measure1.setModel(ModelType.QDM_5_6.getValue());
    measure1.setMeasureMetaData(null);
    measure2.setMeasureMetaData(finalMeasureMetaData);
    measure2.setMeasureSetId(measure1.getMeasureSetId());
    measure1.setCqlLibraryName("");
    doReturn(List.of(measure2))
        .when(measureRepository)
        .findAllByMeasureSetIdAndActive(eq("IDIDID"), eq(true));
    //    doReturn(Optional.of(measure2))
    //        .when(measureRepository)
    //        .findByCqlLibraryName(eq("MSR01Library"));
    when(organizationRepository.findAll()).thenReturn(organizationList);
    doReturn(measure1).when(measureRepository).save(any(Measure.class));
    doReturn(measure1)
        .when(measureTransferService)
        .overwriteExistingMeasure(anyList(), eq(measure1));
    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
    verify(measureRepository, times(1)).save(any(Measure.class));
    verify(measureRepository, times(1)).deleteAll(anyList());
    verify(measureTransferService, times(1)).overwriteExistingMeasure(anyList(), eq(measure1));
  }

  @Test
  void testImportMeasureSuccessEnableRepeatTransferIsTrueAndQdmAndNewLibrary() {
    measure1.setModel(ModelType.QDM_5_6.getValue());
    measure1.setMeasureSetId("3e3e3e");
    doReturn(measure1).when(measureRepository).save(any(Measure.class));
    doReturn(true)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    when(organizationRepository.findAll()).thenReturn(organizationList);

    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");

    assertNotNull(persistedMeasure);

    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure1.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure1.getModel(), ModelType.QDM_5_6.getValue());
    assertEquals(measure1.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure1.getCql(), persistedMeasure.getCql());
    assertEquals(measure1.getGroups().size(), persistedMeasure.getGroups().size());
    assertEquals(
        measure1.getGroups().get(0).getPopulations().get(0).getDescription(),
        persistedMeasure.getGroups().get(0).getPopulations().get(0).getDescription());
    assertEquals(
        measure1.getMeasureMetaData().getReferences().get(0).getReferenceText(),
        persistedMeasure.getMeasureMetaData().getReferences().get(0).getReferenceText());
    assertEquals(
        measure1.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        persistedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser());
    assertEquals(
        measure1.getMeasureMetaData().isDraft(), persistedMeasure.getMeasureMetaData().isDraft());
    assertEquals(
        measure1.getMeasureMetaData().getRiskAdjustment(),
        persistedMeasure.getMeasureMetaData().getRiskAdjustment());
    assertEquals(
        measure1.getMeasureMetaData().getDefinition(),
        persistedMeasure.getMeasureMetaData().getDefinition());
    assertEquals(
        measure1.getMeasureMetaData().isExperimental(),
        persistedMeasure.getMeasureMetaData().isExperimental());
    assertEquals(
        measure1.getMeasureMetaData().getTransmissionFormat(),
        persistedMeasure.getMeasureMetaData().getTransmissionFormat());
    assertEquals(
        measure1.getMeasureMetaData().getSupplementalDataElements(),
        persistedMeasure.getMeasureMetaData().getSupplementalDataElements());

    assertEquals("SB Url", persistedMeasure.getMeasureMetaData().getSteward().getUrl());
    assertEquals(1, persistedMeasure.getMeasureMetaData().getDevelopers().size());
    assertEquals("SB 2 Url", persistedMeasure.getMeasureMetaData().getDevelopers().get(0).getUrl());
  }

  @Test
  void testImportMeasureSuccessEnableRepeatTransferIsFalseAndQdmAndNewLibrary() {
    measure1.setModel(ModelType.QDM_5_6.getValue());
    doReturn(measure1).when(measureRepository).save(any(Measure.class));
    doReturn(false)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    when(organizationRepository.findAll()).thenReturn(organizationList);

    Measure persistedMeasure =
        measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");

    assertNotNull(persistedMeasure);

    assertEquals(measure1.getMeasureSetId(), persistedMeasure.getMeasureSetId());
    assertEquals(measure1.getMeasureName(), persistedMeasure.getMeasureName());
    assertEquals(measure1.getModel(), ModelType.QDM_5_6.getValue());
    assertEquals(measure1.getCqlLibraryName(), persistedMeasure.getCqlLibraryName());
    assertEquals(measure1.getCql(), persistedMeasure.getCql());
    assertEquals(measure1.getGroups().size(), persistedMeasure.getGroups().size());
    assertEquals(
        measure1.getGroups().get(0).getPopulations().get(0).getDescription(),
        persistedMeasure.getGroups().get(0).getPopulations().get(0).getDescription());
    assertEquals(
        measure1.getMeasureMetaData().getReferences().get(0).getReferenceText(),
        persistedMeasure.getMeasureMetaData().getReferences().get(0).getReferenceText());
    assertEquals(
        measure1.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        persistedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser());
    assertEquals(
        measure1.getMeasureMetaData().isDraft(), persistedMeasure.getMeasureMetaData().isDraft());
    assertEquals(
        measure1.getMeasureMetaData().getRiskAdjustment(),
        persistedMeasure.getMeasureMetaData().getRiskAdjustment());
    assertEquals(
        measure1.getMeasureMetaData().getDefinition(),
        persistedMeasure.getMeasureMetaData().getDefinition());
    assertEquals(
        measure1.getMeasureMetaData().isExperimental(),
        persistedMeasure.getMeasureMetaData().isExperimental());
    assertEquals(
        measure1.getMeasureMetaData().getTransmissionFormat(),
        persistedMeasure.getMeasureMetaData().getTransmissionFormat());
    assertEquals(
        measure1.getMeasureMetaData().getSupplementalDataElements(),
        persistedMeasure.getMeasureMetaData().getSupplementalDataElements());

    assertEquals("SB Url", persistedMeasure.getMeasureMetaData().getSteward().getUrl());
    assertEquals(1, persistedMeasure.getMeasureMetaData().getDevelopers().size());
    assertEquals("SB 2 Url", persistedMeasure.getMeasureMetaData().getDevelopers().get(0).getUrl());
  }

  @Test
  void testImportMeasureSuccessNoOrganizationsThrowRuntimeException() {
    measure1.setModel(ModelType.QDM_5_6.getValue());
    doReturn(false)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));

    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
              ;
            });
    String expectedMessage = "No organizations are available";
    String actualMessage = exception.getMessage();

    assertTrue(actualMessage.contains(expectedMessage));

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
  }

  @Test
  void testImportMeasureFailureEnableRepeatTransferIsFalseAndQdmAndExistsInMeasureSet() {
    measure1.setModel(ModelType.QDM_5_6.getValue());
    doReturn(false)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    doReturn(List.of(measure1, measure2))
        .when(measureRepository)
        .findAllByMeasureSetIdAndActive(eq("IDIDID"), eq(true));

    Exception exception =
        assertThrows(
            DuplicateMeasureException.class,
            () -> {
              measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
              ;
            });

    String expectedMessage = "The measure already exists";
    String actualMessage = exception.getMessage();

    assertTrue(actualMessage.contains(expectedMessage));

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
  }

  @Test
  void testImportMeasureFailureEnableRepeatTransferIsFalseAndQdmAndDuplicateCqlLibrary() {
    measure1.setModel(ModelType.QDM_5_6.getValue());
    doReturn(false)
        .when(appConfigService)
        .isFlagEnabled(eq(MadieFeatureFlag.ENABLE_QDM_REPEAT_TRANSFER));
    doReturn(Optional.of(measure2))
        .when(measureRepository)
        .findByCqlLibraryName(eq("MSR01Library"));

    Exception exception =
        assertThrows(
            DuplicateKeyException.class,
            () -> {
              measureService.importMatMeasure(measure1, "1", "TOUCH_DOWN", "akinsgre");
              ;
            });

    String expectedMessage = "CQL library with given name already exists.";
    String actualMessage = exception.getMessage();

    assertTrue(actualMessage.contains(expectedMessage));

    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
  }
}
