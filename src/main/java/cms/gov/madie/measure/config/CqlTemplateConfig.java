package cms.gov.madie.measure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Getter
@Configuration
public class CqlTemplateConfig {
  @Value("${madie.service-config.cql-template-qicore411-url}")
  private String qicore411CqlTemplateUrl;

  @Value("${madie.service-config.cql-template-qdm56-url}")
  private String qdm56CqlTemplateUrl;

  @Value("${madie.service-config.cql-template-qicore600-url}")
  private String qicore600CqlTemplateUrl;
}
