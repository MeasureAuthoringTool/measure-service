package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.ElmTranslatorClientConfig;
import cms.gov.madie.measure.exceptions.CqlElmTranslationServiceException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.ElmJson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElmTranslatorClientTest {

  @Mock private ElmTranslatorClientConfig elmTranslatorClientConfig;
  @Mock private RestTemplate elmTranslatorRestTemplate;

  @InjectMocks private ElmTranslatorClient elmTranslatorClient;

  @BeforeEach
  void beforeEach() {
    lenient()
        .when(elmTranslatorClientConfig.getFhirCqlElmServiceBaseUrl())
        .thenReturn("http://test");
    lenient()
        .when(elmTranslatorClientConfig.getQdmCqlElmServiceBaseUrl())
        .thenReturn("http://test");
    lenient()
        .when(elmTranslatorClientConfig.getCqlElmServiceElmJsonUri())
        .thenReturn("/cql/translator/cql");
    lenient()
        .when(elmTranslatorClientConfig.getCqlToElmTranslatorVersionUri())
        .thenReturn("/cql/translator/version");
  }

  @Test
  void testGetCqlToElmTranslatorVersion() {
    when(elmTranslatorRestTemplate.getForObject(any(URI.class), eq(String.class)))
        .thenReturn("1.5.0");
    String output = elmTranslatorClient.getCqlToElmTranslatorVersion(ModelType.QDM_5_6.getValue());
    assertThat(output, is(equalTo("1.5.0")));
  }

  @Test
  void testGetCqlToElmTranslatorVersionReturnsNullOnError() {
    when(elmTranslatorRestTemplate.getForObject(any(URI.class), eq(String.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
    String output = elmTranslatorClient.getCqlToElmTranslatorVersion(ModelType.QDM_5_6.getValue());
    assertThat(output, is(equalTo(null)));
  }

  @Test
  void testRestTemplateHandlesClientErrorException() {
    when(elmTranslatorRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
    assertThrows(
        CqlElmTranslationServiceException.class,
        () -> elmTranslatorClient.getElmJson("TEST_CQL", "QDM v5.6"));
  }

  @Test
  void testRestTemplateReturnsElmJson() {
    ElmJson elmJson = ElmJson.builder().json("{}").xml("<></>").build();
    when(elmTranslatorRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok(elmJson));
    ElmJson output = elmTranslatorClient.getElmJson("TEST_CQL", "QDM v5.6");
    assertThat(output, is(equalTo(elmJson)));
  }

  @Test
  void testHasErrorsHandlesNull() {
    boolean output = elmTranslatorClient.hasErrors(null);
    assertThat(output, is(true));
  }

  @Test
  void testHasErrorsHandlesMalformedJson() {
    ElmJson elmJson = ElmJson.builder().json("NOT_JSON").build();
    assertThrows(
        CqlElmTranslationServiceException.class, () -> elmTranslatorClient.hasErrors(elmJson));
  }

  @Test
  void testHasErrorsReturnsTrue() {
    final String json =
        "{\n"
            + "          \"errorExceptions\": [{\n"
            + "                                  \"startLine\" : 2,\n"
            + "                                  \"startChar\" : 1,\n"
            + "                                  \"endLine\" : 2,\n"
            + "                                  \"endChar\" : 6,\n"
            + "                                  \"errorType\" : null,\n"
            + "                                  \"errorSeverity\" : \"Error\",\n"
            + "                                  \"targetIncludeLibraryId\" : \"TestLib\",\n"
            + "                                  \"targetIncludeLibraryVersionId\" : \"2\",\n"
            + "                                  \"type\" : null,\n"
            + "                                  \"message\" : \"Could not resolve identifier define in the current library.\"\n"
            + "                                }]\n"
            + "        }";
    ElmJson elmJson = ElmJson.builder().json(json).build();
    boolean output = elmTranslatorClient.hasErrors(elmJson);
    assertThat(output, is(true));
  }

  @Test
  void testHasErrorsReturnsFalseForEmptyArray() {
    final String json = "{\"errorExceptions\": []}";
    ElmJson elmJson = ElmJson.builder().json(json).build();
    boolean output = elmTranslatorClient.hasErrors(elmJson);
    assertThat(output, is(false));
  }

  @Test
  void testHasErrorsReturnsFalseForNullFieldValue() {
    final String json = "{\"errorExceptions\": null}";
    ElmJson elmJson = ElmJson.builder().json(json).build();
    boolean output = elmTranslatorClient.hasErrors(elmJson);
    assertThat(output, is(false));
  }

  @Test
  void testHasErrorsReturnsFalseForMissingField() {
    final String json =
        "{\n"
            + "          \"library\" : {\n"
            + "            \"annotation\" : [ { } ]\n"
            + "          }\n"
            + "        }";
    ElmJson elmJson = ElmJson.builder().json(json).build();
    boolean output = elmTranslatorClient.hasErrors(elmJson);
    assertThat(output, is(false));
  }

  @Test
  void testGetTranslatorVersionFromElmJsonReturnsNullForBlankInput() {
    assertThat(elmTranslatorClient.getTranslatorVersionFromElmJson(null), is(equalTo(null)));
    assertThat(elmTranslatorClient.getTranslatorVersionFromElmJson(""), is(equalTo(null)));
    assertThat(elmTranslatorClient.getTranslatorVersionFromElmJson("   "), is(equalTo(null)));
  }

  @Test
  void testGetTranslatorVersionFromElmJsonReturnsVersion() {
    String elmJson =
        "{"
            + "\"library\": {"
            + "  \"annotation\": ["
            + "    {\"type\": \"Annotation\", \"s\": {}},"
            + "    {\"type\": \"CqlToElmInfo\", \"translatorVersion\": \"3.5.1\"}"
            + "  ]"
            + "}}";
    String version = elmTranslatorClient.getTranslatorVersionFromElmJson(elmJson);
    assertThat(version, is(equalTo("3.5.1")));
  }

  @Test
  void testGetTranslatorVersionFromElmJsonReturnsNullWhenNoMatchingAnnotation() {
    String elmJson =
        "{"
            + "\"library\": {"
            + "  \"annotation\": ["
            + "    {\"type\": \"Annotation\", \"s\": {}}"
            + "  ]"
            + "}}";
    assertThat(elmTranslatorClient.getTranslatorVersionFromElmJson(elmJson), is(equalTo(null)));
  }

  @Test
  void testGetTranslatorVersionFromElmJsonReturnsNullOnInvalidJson() {
    assertThat(
        elmTranslatorClient.getTranslatorVersionFromElmJson("not valid json"), is(equalTo(null)));
  }

  @Test
  void testQiCoreGetElmJsonURI() {
    URI uri = elmTranslatorClient.getElmJsonURI(ModelType.QI_CORE.getValue());
    assertEquals(
        "http://test/cql/translator/cql?checkContext=true&errorSeverity=Info", uri.toString());
  }
}
