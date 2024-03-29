package cms.gov.madie.measure.resources;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import cms.gov.madie.measure.repositories.OrganizationRepository;
import cms.gov.madie.measure.services.ActionLogService;
import cms.gov.madie.measure.services.AppConfigService;
import cms.gov.madie.measure.services.ElmTranslatorClient;
import cms.gov.madie.measure.services.MeasureService;
import cms.gov.madie.measure.services.MeasureSetService;
import cms.gov.madie.measure.services.MeasureTransferService;
import cms.gov.madie.measure.services.VersionService;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.measure.*;
import gov.cms.madie.models.common.Version;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class MeasureTransferControllerTest {

  private static final String LAMBDA_TEST_API_KEY = "TOUCH-DOWN";

  private Measure measure;
  private MeasureSet measureSet;

  private List<Organization> organizationList;

  @Mock private MeasureService measureService;
  @Mock private MeasureRepository repository;
  @Mock private MeasureSetRepository measureSetRepository;
  @Mock private MeasureSetService measureSetService;
  @Mock private ActionLogService actionLogService;
  @Mock private ElmTranslatorClient elmTranslatorClient;
  @Mock private AppConfigService appConfigService;
  @Mock private VersionService versionService;
  @Mock private MeasureTransferService measureTransferService;

  @Mock private OrganizationRepository organizationRepository;
  @Mock private ElmJson elmJson;
  private static final String CQL =
      "library MedicationDispenseTest version '0.0.001' using FHIR version '4.0.1'";
  private static final String ELM_JSON_SUCCESS = "{\"result\":\"success\"}";
  private static final String ELM_JSON_FAIL =
      "{\"errorExceptions\": [{\"Error\":\"UNAUTHORIZED\"}]}";

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;
  @Captor private ArgumentCaptor<Class> targetClassArgumentCaptor;
  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;
  @Captor private ArgumentCaptor<String> performedByArgumentCaptor;

  @InjectMocks private MeasureTransferController controller;

  MockHttpServletRequest request;

  List<Group> groups;

  String cmsId;

  @BeforeEach
  public void setUp() {
    request = new MockHttpServletRequest();
    groups =
        List.of(
            new Group(
                "id-abc",
                "Ratio",
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        "test description"),
                    new Population(
                        "id-2",
                        PopulationType.DENOMINATOR,
                        "Denominator",
                        null,
                        "test description denom"),
                    new Population(
                        "id-3",
                        PopulationType.DENOMINATOR_EXCEPTION,
                        "Denominator Exceptions",
                        null,
                        "test description denom excep"),
                    new Population(
                        "id-4",
                        PopulationType.NUMERATOR,
                        "Numerator",
                        null,
                        "test description num")),
                List.of(
                    new MeasureObservation(
                        "mo-id-1",
                        "ipp",
                        "a description of ipp",
                        null,
                        AggregateMethodType.AVERAGE.getValue())),
                "Description",
                "improvmentNotation",
                "rateAggragation",
                List.of(MeasureGroupTypes.PROCESS),
                "testScoringUnit",
                List.of(new Stratification()),
                "populationBasis"));
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
                .endorsementId("test EndorsementId")
                .build());

    List<Organization> developersList = new ArrayList<>();
    developersList.add(Organization.builder().name("SB 2").build());
    developersList.add(Organization.builder().name("SB 3").build());

    var measureMetaData =
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

    measure =
        Measure.builder()
            .id("testId")
            .createdBy("testCreatedBy")
            .measureSetId("abc-pqr-xyz")
            .version(new Version(0, 0, 0))
            .measureName("MedicationDispenseTest")
            .cqlLibraryName("MedicationDispenseTest")
            .model("QI-Core")
            .measureMetaData(measureMetaData)
            .groups(groups)
            .cql(CQL)
            .cqlErrors(false)
            .elmJson(ELM_JSON_SUCCESS)
            .testCases(List.of(TestCase.builder().id("testCaseId").build()))
            .build();

    cmsId = "1";

    measureSet = MeasureSet.builder().id(null).measureSetId("abc-pqr-xyz").owner("testID").build();

    organizationList = new ArrayList<>();
    organizationList.add(Organization.builder().name("SB").url("SB Url").build());
    organizationList.add(Organization.builder().name("SB 2").url("SB 2 Url").build());
    organizationList.add(Organization.builder().name("CancerLinQ").url("CancerLinQ Url").build());
    organizationList.add(Organization.builder().name("Innovaccer").url("Innovaccer Url").build());
  }

  @Test
  public void createMeasureSuccessTest() {
    request.addHeader("harp-id", "akinsgre");
    ArgumentCaptor<Measure> persistedMeasureArgCaptor = ArgumentCaptor.forClass(Measure.class);
    doReturn(measure)
        .when(measureService)
        .importMatMeasure(
            any(Measure.class), any(String.class), any(String.class), any(String.class));

    ResponseEntity<Measure> response =
        controller.createMeasure(request, measure, cmsId, LAMBDA_TEST_API_KEY);

    verify(measureService, times(1))
        .importMatMeasure(
            any(Measure.class), any(String.class), any(String.class), any(String.class));
    Measure persistedMeasure = response.getBody();
    assertNotNull(persistedMeasure);
  }
}
