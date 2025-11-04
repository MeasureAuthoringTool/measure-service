package cms.gov.madie.measure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Getter
@Configuration
public class ExcelConfig {

  @Value("${madie.excel-export-service.base-url}")
  private String excelExportServiceBaseUrl;

  @Value("${madie.excel-export-service.get-excel-urn}")
  private String getExcelUrn;

  @Bean
  public RestTemplate excelRestTemplate() {
    return new RestTemplate();
  }
}
