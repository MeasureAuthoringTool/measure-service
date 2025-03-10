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
import org.springframework.http.HttpHeaders;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElmTranslatorClientTest {

  @Mock private ElmTranslatorClientConfig elmTranslatorClientConfig;
  @Mock private RestTemplate restTemplate;

  @InjectMocks private ElmTranslatorClient elmTranslatorClient;

  private final String HEADER_1 = "api-key";

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
  }

  @Test
  void testRestTemplateHandlesClientErrorException() {
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
    assertThrows(
        CqlElmTranslationServiceException.class,
        () -> elmTranslatorClient.getElmJson("TEST_CQL", "QDM v5.6", "TEST_TOKEN"));
  }

  @Test
  void testRestTemplateReturnsElmJson() {
    ElmJson elmJson = ElmJson.builder().json("{}").xml("<></>").build();
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok(elmJson));
    ElmJson output = elmTranslatorClient.getElmJson("TEST_CQL", "QDM v5.6", "TEST_TOKEN");
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
  void testQiCoreGetElmJsonURI() {
    URI uri = elmTranslatorClient.getElmJsonURI(ModelType.QI_CORE.getValue());
    assertEquals("http://test/cql/translator/cql?checkContext=true&ErrorSeverity=Info", uri.toString());
  }

  @Test
  void testGetCqlHttpEntity() {
    HttpEntity<String> httpEntity =
        elmTranslatorClient.getCqlHttpEntity("test cql", null, HEADER_1 + "1", "HARP_ID1");
    HttpHeaders headers = httpEntity.getHeaders();
    assertEquals(headers.get(HEADER_1).get(0), HEADER_1 + "1");
    assertEquals(headers.get("harp-id").get(0), "HARP_ID1");
  }
}
