package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
import cms.gov.madie.measure.utils.MeasureUtil;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureGroupTypes;
import gov.cms.madie.models.measure.MeasureMetaData;
import gov.cms.madie.models.measure.MeasureSet;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.TestCase;
import gov.cms.madie.models.validators.ValidLibraryNameValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {
  @Mock private PackageServiceFactory packageServiceFactory;
  @Mock private ModelValidatorFactory modelValidatorFactory;
  @Mock private QicorePackageService qicorePackageService;
  @Mock private QdmPackageService qdmPackageService;
  @Mock private QiCoreModelValidator qicoreModelValidator;
  @Mock private QdmModelValidator qdmModelValidator;
  @Mock private ValidLibraryNameValidator validLibraryNameValidator;
  @Mock private MeasureUtil measureUtil;
  @InjectMocks ExportService exportService;

  private final String packageContent = "raw package";
  private final String token = "token";
  private Measure measure;

  @BeforeEach
  void setup() {
    Group group =
        Group.builder()
            .scoring("Cohort")
            .populationBasis("Encounter")
            .measureGroupTypes(List.of(MeasureGroupTypes.OUTCOME))
            .populations(
                List.of(
                    new Population(
                        "id-1",
                        PopulationType.INITIAL_POPULATION,
                        "Initial Population",
                        null,
                        null,
                        "IntialPopulation_1")))
            .groupDescription("Description")
            .scoringUnit("test-scoring-unit")
            .build();
    measure =
        Measure.builder()
            .id("measure-id")
            .createdBy("test.user")
            .groups(List.of(group))
            .measureMetaData(MeasureMetaData.builder().draft(true).build())
            .measureSet(MeasureSet.builder().owner("test.user").build())
            .build();
    MeasureMetaData measureMetaData =
        MeasureMetaData.builder()
            .draft(false)
            .steward(Organization.builder().name("SemanticBits").build())
            .description("This is a description")
            .developers(List.of(Organization.builder().name("ICF").build()))
            .build();
    measure.setMeasureMetaData(measureMetaData);
    TestCase testCase = TestCase.builder().build();
    measure.setTestCases(List.of(testCase));
  }

  @Test
  void testGetQdmMeasurePackage() {
    when(modelValidatorFactory.getModelValidator(any())).thenReturn(qdmModelValidator);
    doNothing().when(qdmModelValidator).validateGroups(any(Measure.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));
    when(packageServiceFactory.getPackageService(any())).thenReturn(qdmPackageService);
    when(qdmPackageService.getMeasurePackage(any(Measure.class), anyBoolean(), anyString()))
        .thenReturn(
            PackageDto.builder()
                .fromStorage(false)
                .exportPackage(packageContent.getBytes())
                .build());
    PackageDto output = exportService.getMeasureExport(measure, token, "Info");
    byte[] measurePackage = output.getExportPackage();
    assertEquals(new String(measurePackage), packageContent);
  }

  @Test
  void testGetQiCoreMeasurePackage() {
    when(modelValidatorFactory.getModelValidator(any())).thenReturn(qicoreModelValidator);
    doNothing().when(qicoreModelValidator).validateGroups(any(Measure.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));
    when(packageServiceFactory.getPackageService(any())).thenReturn(qicorePackageService);
    when(qicorePackageService.getMeasurePackage(any(Measure.class), anyBoolean(), anyString()))
        .thenReturn(
            PackageDto.builder()
                .fromStorage(false)
                .exportPackage(packageContent.getBytes())
                .build());
    PackageDto output = exportService.getMeasureExport(measure, token, "Info");
    byte[] measurePackage = output.getExportPackage();
    assertEquals(new String(measurePackage), packageContent);
  }

  @Test
  void testGetMeasurePackageWhenMetaDataIsNull() {
    measure.setMeasureMetaData(null);
    when(modelValidatorFactory.getModelValidator(any())).thenReturn(qdmModelValidator);
    doNothing().when(qdmModelValidator).validateGroups(any(Measure.class));
    when(measureUtil.validateAllMeasureDependencies(any(Measure.class)))
        .thenAnswer((invocationOnMock) -> invocationOnMock.getArgument(0));
    when(packageServiceFactory.getPackageService(any())).thenReturn(qdmPackageService);
    PackageDto packageDto =
        PackageDto.builder().fromStorage(false).exportPackage(packageContent.getBytes()).build();
    when(qdmPackageService.getMeasurePackage(any(Measure.class), anyBoolean(), anyString()))
        .thenReturn(packageDto);
    PackageDto output = exportService.getMeasureExport(measure, token, "Info");
    byte[] measurePackage = output.getExportPackage();
    assertEquals(new String(measurePackage), packageContent);
  }

  @Test
  void testGetQRDA() {
    when(packageServiceFactory.getPackageService(any())).thenReturn(qdmPackageService);
    when(qdmPackageService.getQRDA(any(QrdaRequestDTO.class), anyString()))
        .thenReturn(packageContent.getBytes());
    byte[] measurePackage =
        exportService.getQRDA(QrdaRequestDTO.builder().measure(measure).build(), token);
    assertEquals(new String(measurePackage), packageContent);
  }

  @Test
  void testGetQRDANoTestCases() {
    measure.setTestCases(Collections.emptyList());
    Exception ex =
        Assertions.assertThrows(
            InvalidResourceStateException.class,
            () -> exportService.getQRDA(QrdaRequestDTO.builder().measure(measure).build(), token));
    assertEquals(
        ex.getMessage(),
        "Response could not be completed for Measure with ID measure-id, since there are no test cases in the measure.");
  }
}
