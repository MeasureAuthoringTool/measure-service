package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cms.gov.madie.measure.config.CqlTemplateConfig;

@ExtendWith(MockitoExtension.class)
public class CqlTemplateConfigServiceTest {
  private final String QICORE_4_1_1_TEMPLATE = "/QICore411_CQLTemplate.txt";
  private final String QDM_5_6_TEMPLATE = "/QDM56_CQLTemplate.txt";
  private final String QICORE_USING = "using QICore version '4.1.1'";
  private final String QDM_USING = "using QDM version '5.6'";
  private final String QICORE_6_0_0_TEMPLATE = "/QICore600_CQLTemplate.txt";
  private final String QICORE_6_0_0_USING = "using QICore version '6.0.0'";

  @Mock CqlTemplateConfig cqlTemplateConfig;

  @InjectMocks CqlTemplateConfigService cqlTemplateConfigService;

  @Test
  public void testReadCqlFileContentQicore() throws Exception {
    String qicoreTemplateFilePath = this.getClass().getResource(QICORE_4_1_1_TEMPLATE).getPath();
    String qicoreTemplate =
        cqlTemplateConfigService.readCqlFileContent("file:" + qicoreTemplateFilePath);

    assertTrue(qicoreTemplate.contains(QICORE_USING));
  }

  @Test
  public void testReadCqlFileContentQdm() throws Exception {
    String qdmTemplateFilePath = this.getClass().getResource(QDM_5_6_TEMPLATE).getPath();
    String qdmTemplate = cqlTemplateConfigService.readCqlFileContent("file:" + qdmTemplateFilePath);

    assertTrue(qdmTemplate.contains(QDM_USING));
  }

  @Test
  public void testReadCqlFileContentMalformedURLException() {
    String cqlTemplate = cqlTemplateConfigService.readCqlFileContent("ht://aws.com");
    assertNull(cqlTemplate);
  }

  @Test
  public void testReadCqlFileContentIOException() {
    String cqlTemplate = cqlTemplateConfigService.readCqlFileContent("file:/test.cql");
    assertNull(cqlTemplate);
  }

  @Test
  public void testGetQiCore411CqlTemplate() throws Exception {
    String templateFile =
        IOUtils.toString(this.getClass().getResourceAsStream(QICORE_4_1_1_TEMPLATE), "UTF-8");
    ReflectionTestUtils.setField(cqlTemplateConfigService, "qicore411CqlTemplate", templateFile);

    String qicoreTemplate = cqlTemplateConfigService.getQiCore411CqlTemplate();

    assertTrue(qicoreTemplate.contains(QICORE_USING));
  }

  @Test
  public void testGetQdm56CqlTemplate() throws Exception {
    String templateFile =
        IOUtils.toString(this.getClass().getResourceAsStream(QDM_5_6_TEMPLATE), "UTF-8");
    ReflectionTestUtils.setField(cqlTemplateConfigService, "qdm56CqlTemplate", templateFile);

    String qdmTemplate = cqlTemplateConfigService.getQdm56CqlTemplate();

    assertTrue(qdmTemplate.contains(QDM_USING));
  }

  @Test
  public void testGetQiCore600CqlTemplate() throws Exception {
    String templateFile =
        IOUtils.toString(this.getClass().getResourceAsStream(QICORE_6_0_0_TEMPLATE), "UTF-8");
    ReflectionTestUtils.setField(cqlTemplateConfigService, "qicore600CqlTemplate", templateFile);

    String qicore600Template = cqlTemplateConfigService.getQiCore600CqlTemplate();

    assertTrue(qicore600Template.contains(QICORE_6_0_0_USING));
  }
}
