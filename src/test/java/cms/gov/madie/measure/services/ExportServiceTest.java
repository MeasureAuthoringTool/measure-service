package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
import cms.gov.madie.measure.utils.MeasureUtil;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.dto.OverlappingCodeDto;
import gov.cms.madie.models.dto.OverlappingValueSetDto;
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

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
  private OverlappingCodeDto overlappingCodeDto;
  private OverlappingValueSetDto overlappingValueSetDto;

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

    overlappingCodeDto =
        OverlappingCodeDto.builder()
            .code("4525004")
            .codeSystem("http://snomed.info/sct")
            .description("Emergency department patient visit (procedure)")
            .codeSystemName("http://snomed.info/sct")
            .codeSystemVersion("http://snomed.info/sct/731000124108/version/20250301")
            .build();
    overlappingValueSetDto =
        OverlappingValueSetDto.builder()
            .name("EmergencyDepartmentEvaluationAndManagementVisit")
            .oid("2.16.840.1.113883.3.464.1003.101.12.1010")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.101.12.1010")
            .build();
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

  @Test
  void testGetOverlappingValueSets() throws EncryptedDocumentException, IOException {
    overlappingCodeDto.setValueSets(List.of(overlappingValueSetDto));
    byte[] bytes = exportService.getOverlappingValueSets(List.of(overlappingCodeDto));

    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    Workbook workbook = WorkbookFactory.create(bis);
    int sheets = workbook.getNumberOfSheets();
    assertEquals(1, sheets);

    Sheet sheet = workbook.getSheetAt(0);
    String sheetName = sheet.getSheetName();
    assertEquals("overlapping-codes", sheetName);

    Row row = sheet.getRow(0);
    assertEquals(6, row.getPhysicalNumberOfCells());

    assertEquals("Code", getCellValue(row, 0));
    assertEquals("Code System", getCellValue(row, 1));
    assertEquals("Description", getCellValue(row, 2));
    assertEquals("Version", getCellValue(row, 3));
    assertEquals("Value Set", getCellValue(row, 4));
    assertEquals("Value Set OID/URL", getCellValue(row, 5));

    Row row2 = sheet.getRow(1);
    assertEquals(6, row2.getPhysicalNumberOfCells());

    assertEquals("4525004", getCellValue(row2, 0));
    assertEquals("http://snomed.info/sct", getCellValue(row2, 1));
    assertEquals("Emergency department patient visit (procedure)", getCellValue(row2, 2));
    assertEquals("http://snomed.info/sct/731000124108/version/20250301", getCellValue(row2, 3));
    assertEquals("EmergencyDepartmentEvaluationAndManagementVisit", getCellValue(row2, 4));
    assertEquals("2.16.840.1.113883.3.464.1003.101.12.1010", getCellValue(row2, 5));
  }

  private String getCellValue(Row row, int cellNumber) {
    Cell cell = row.getCell(cellNumber);
    return cell.getStringCellValue();
  }

  @Test
  void testGetOverlappingValueSetsNoValueSets() throws EncryptedDocumentException, IOException {
    overlappingCodeDto.setValueSets(null);
    byte[] bytes = exportService.getOverlappingValueSets(List.of(overlappingCodeDto));

    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    Workbook workbook = WorkbookFactory.create(bis);
    int sheets = workbook.getNumberOfSheets();
    assertEquals(1, sheets);

    Sheet sheet = workbook.getSheetAt(0);
    Row row2 = sheet.getRow(1);
    assertNotNull(row2);
    assertEquals("4525004", getCellValue(row2, 0));
    assertEquals("http://snomed.info/sct", getCellValue(row2, 1));
    assertEquals("Emergency department patient visit (procedure)", getCellValue(row2, 2));
    assertEquals("http://snomed.info/sct/731000124108/version/20250301", getCellValue(row2, 3));
    assertEquals("", getCellValue(row2, 4));
    assertEquals("", getCellValue(row2, 5));
  }
}
