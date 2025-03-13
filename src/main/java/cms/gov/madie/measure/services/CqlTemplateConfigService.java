package cms.gov.madie.measure.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import cms.gov.madie.measure.config.CqlTemplateConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CqlTemplateConfigService {

  private final CqlTemplateConfig cqlTemplateConfig;
  private String qicore411CqlTemplate;
  private String qdm56CqlTemplate;
  private String qicore600CqlTemplate;

  @Autowired
  public CqlTemplateConfigService(CqlTemplateConfig cqlTemplateConfig) {
    this.cqlTemplateConfig = cqlTemplateConfig;
  }

  @PostConstruct
  @Scheduled(cron = "0 */5 * * * *")
  public void refreshCqlTemplateConfig() {
    qicore411CqlTemplate = readCqlFileContent(cqlTemplateConfig.getQicore411CqlTemplateUrl());
    qdm56CqlTemplate = readCqlFileContent(cqlTemplateConfig.getQdm56CqlTemplateUrl());
    qicore600CqlTemplate = readCqlFileContent(cqlTemplateConfig.getQicore600CqlTemplateUrl());
  }

  String readCqlFileContent(String url) {
    String content = null;
    try {
      InputStream inputStream = URI.create(url).toURL().openStream();
      content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
      log.info("Initializing measure-service with CQL Template: {}", url);
    } catch (MalformedURLException mfEx) {
      log.error("MalformedURLException occurred while initializing CQL Template Config! - ", mfEx);
    } catch (IOException ioEx) {
      log.error("IOException occurred while initializing CQL Template Config! - ", ioEx);
    }
    return content;
  }

  public String getQiCore411CqlTemplate() {
    return this.qicore411CqlTemplate;
  }

  public String getQdm56CqlTemplate() {
    return this.qdm56CqlTemplate;
  }

  public String getQiCore600CqlTemplate() {
    return this.qicore600CqlTemplate;
  }
}
