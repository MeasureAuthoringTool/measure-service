package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.InvalidResourceStateException;
import cms.gov.madie.measure.factories.ModelValidatorFactory;
import cms.gov.madie.measure.factories.PackageServiceFactory;
import cms.gov.madie.measure.utils.MeasureUtil;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.dto.OverlappingCodeDto;
import gov.cms.madie.models.dto.OverlappingValueSetDto;
import gov.cms.madie.models.measure.Measure;
import lombok.AllArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
// import org.dhatim.fastexcel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@AllArgsConstructor
public class ExportService {
  private final PackageServiceFactory packageServiceFactory;
  private final ModelValidatorFactory modelValidatorFactory;
  private final MeasureUtil measureUtil;

  public PackageDto getMeasureExport(Measure measure, String accessToken, String elmErrorSeverity) {
    ModelValidator modelValidator =
        modelValidatorFactory.getModelValidator(ModelType.valueOfName(measure.getModel()));
    measure = measureUtil.validateAllMeasureDependencies(measure);
    modelValidator.validateMetadata(measure);
    modelValidator.validateGroups(measure);
    modelValidator.validateCqlErrors(measure);
    PackageService packageService =
        packageServiceFactory.getPackageService(ModelType.valueOfName(measure.getModel()));
    boolean errorsOnly = elmErrorSeverity.equals("Error");
    return packageService.getMeasurePackage(measure, !errorsOnly, accessToken);
  }

  public byte[] getQRDA(QrdaRequestDTO qrdaRequestDTO, String accessToken) {
    if (CollectionUtils.isEmpty(qrdaRequestDTO.getMeasure().getTestCases())) {
      throw new InvalidResourceStateException(
          "Measure",
          qrdaRequestDTO.getMeasure().getId(),
          "since there are no test cases in the measure.");
    }
    PackageService packageService =
        packageServiceFactory.getPackageService(
            ModelType.valueOfName(qrdaRequestDTO.getMeasure().getModel()));
    return packageService.getQRDA(qrdaRequestDTO, accessToken);
  }

  public byte[] getOverlappingValueSets(List<OverlappingCodeDto> requestDTOs) {
    XSSFWorkbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("overlapping-codes");

    Row row0 = sheet.createRow(0);
    createHeader(row0);
    XSSFColor color = getXSSFColor(52, 186, 235);
    XSSFFont font = getXSSFFont(workbook, IndexedColors.WHITE.getIndex());
    setStyle(workbook, row0, color, font);

    createRowData(requestDTOs, sheet);

    autoSizeColumns(sheet);

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      workbook.write(bos);
      bos.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
    return bos.toByteArray();
  }

  private void autoSizeColumns(Sheet sheet) {
    for (int columnIndex = 0; columnIndex < 6; columnIndex++) {
      sheet.autoSizeColumn(columnIndex);
    }
  }

  private void createHeader(Row row0) {
    row0.createCell(0).setCellValue("Code");
    row0.createCell(1).setCellValue("Code System");
    row0.createCell(2).setCellValue("Description");
    row0.createCell(3).setCellValue("Version");
    row0.createCell(4).setCellValue("Value Set");
    row0.createCell(5).setCellValue("Value Set OID/URL");
  }

  private XSSFColor getXSSFColor(int i, int j, int k) {
    byte[] rgb = new byte[3];
    rgb[0] = (byte) i;
    rgb[1] = (byte) j;
    rgb[2] = (byte) k;
    return new XSSFColor(rgb);
  }

  private XSSFFont getXSSFFont(XSSFWorkbook workbook, short color) {
    XSSFFont font = workbook.createFont();
    font.setColor(color);
    return font;
  }

  private void setStyle(XSSFWorkbook workbook, Row row0, XSSFColor color, XSSFFont font) {
    XSSFCellStyle cellStyle = workbook.createCellStyle();
    cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    cellStyle.setFillForegroundColor(color);
    cellStyle.setFont(font);
    for (int i = 0; i < row0.getLastCellNum(); i++) {
      row0.getCell(i).setCellStyle(cellStyle);
    }
  }

  private void createRowData(List<OverlappingCodeDto> requestDTOs, Sheet sheet) {
    List<OverlappingCodeDto> newDTOs = getExpandedList(requestDTOs);
    for (int i = 0; i < newDTOs.size(); i++) {
      OverlappingCodeDto requestDto = newDTOs.get(i);
      List<OverlappingValueSetDto> overlappingValueSetDtos = requestDto.getValueSets();
      Row row = sheet.createRow(i + 1);
      populateCode(
          row, requestDto, overlappingValueSetDtos != null ? overlappingValueSetDtos.get(0) : null);
    }
  }

  private List<OverlappingCodeDto> getExpandedList(List<OverlappingCodeDto> requestDTOs) {
    List<OverlappingCodeDto> newDTOs = new ArrayList<>();
    requestDTOs.stream()
        .forEach(
            requestDto -> {
              if (!CollectionUtils.isEmpty(requestDto.getValueSets())) {
                requestDto.getValueSets().stream()
                    .forEach(
                        valueset -> {
                          OverlappingCodeDto codeDto =
                              OverlappingCodeDto.builder()
                                  .code(requestDto.getCode())
                                  .description(requestDto.getDescription())
                                  .codeSystem(requestDto.getCodeSystem())
                                  .codeSystemName(requestDto.getCodeSystemName())
                                  .codeSystemVersion(requestDto.getCodeSystemVersion())
                                  .build();
                          codeDto.setValueSets(List.of(valueset));
                          newDTOs.add(codeDto);
                        });
              } else {
                newDTOs.add(requestDto);
              }
            });
    return newDTOs;
  }

  private void populateCode(
      Row row, OverlappingCodeDto requestDto, OverlappingValueSetDto overlappingValueSetDto) {
    row.createCell(0).setCellValue(requestDto.getCode());
    row.createCell(1).setCellValue(requestDto.getCodeSystem());
    row.createCell(2).setCellValue(requestDto.getDescription());
    row.createCell(3).setCellValue(requestDto.getCodeSystemVersion());
    row.createCell(4)
        .setCellValue(overlappingValueSetDto != null ? overlappingValueSetDto.getName() : "");
    row.createCell(5)
        .setCellValue(overlappingValueSetDto != null ? overlappingValueSetDto.getOid() : "");
  }
}
