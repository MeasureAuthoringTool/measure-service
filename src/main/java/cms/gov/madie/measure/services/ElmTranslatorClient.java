package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.ElmTranslatorClientConfig;
import cms.gov.madie.measure.exceptions.CqlElmTranslationServiceException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.ElmJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Service
@AllArgsConstructor
public class ElmTranslatorClient {

  private ElmTranslatorClientConfig elmTranslatorClientConfig;
  private RestTemplate elmTranslatorRestTemplate;

  public ElmJson getElmJson(final String cql, String measureModel, String accessToken) {
    return getElmJson(cql, measureModel, accessToken, CqlCompilerException.ErrorSeverity.Info);
  }

  public ElmJson getElmJson(
      final String cql,
      String measureModel,
      String accessToken,
      CqlCompilerException.ErrorSeverity errorSeverity) {
    try {
      URI uri = getElmJsonURI(measureModel, errorSeverity);
      HttpEntity<String> cqlEntity = getCqlHttpEntity(cql, accessToken, null, null);
      return elmTranslatorRestTemplate
          .exchange(uri, HttpMethod.PUT, cqlEntity, ElmJson.class)
          .getBody();
    } catch (Exception ex) {
      log.error("An error occurred calling the CQL to ELM translation service", ex);
      throw new CqlElmTranslationServiceException(
          "There was an error calling CQL-ELM translation service", ex);
    }
  }

  public boolean hasErrors(ElmJson elmJson) {
    if (elmJson == null) {
      return true;
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.readTree(elmJson.getJson());
      return jsonNode.has("errorExceptions") && jsonNode.get("errorExceptions").size() > 0;
    } catch (Exception ex) {
      log.error("An error occurred parsing the response from the CQL-ELM translation service", ex);
      throw new CqlElmTranslationServiceException(
          "There was an error calling CQL-ELM translation service", ex);
    }
  }
  //overload method invocation so if we don't provide ErrorSeverity we assume that its info
  protected URI getElmJsonURI(String measureModel) {
    return getElmJsonURI(measureModel, CqlCompilerException.ErrorSeverity.Info);
  }
  protected URI getElmJsonURI(
      String measureModel, CqlCompilerException.ErrorSeverity errorSeverity) {
    var isQdm = StringUtils.equals(measureModel, ModelType.QDM_5_6.getValue());
    String baseUrl =
        isQdm
            ? elmTranslatorClientConfig.getQdmCqlElmServiceBaseUrl()
            : elmTranslatorClientConfig.getFhirCqlElmServiceBaseUrl();
    URI uri = null;
    if (!isQdm) {
      uri =
          UriComponentsBuilder.fromHttpUrl(
                  baseUrl + elmTranslatorClientConfig.getCqlElmServiceElmJsonUri())
              .queryParam("checkContext", true)
              .queryParam("errorSeverity", errorSeverity)
              .build()
              .encode()
              .toUri();
    } else {
      uri = URI.create(baseUrl + elmTranslatorClientConfig.getCqlElmServiceElmJsonUri());
    }
    return uri;
  }

  protected HttpEntity<String> getCqlHttpEntity(
      final String cql, String accessToken, String apiKey, String harpId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_PLAIN);
    if (accessToken != null) {
      headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    } else if (apiKey != null && harpId != null) {
      headers.set("api-key", apiKey);
      headers.set("harp-id", harpId);
    }
    return new HttpEntity<>(cql, headers);
  }
}
