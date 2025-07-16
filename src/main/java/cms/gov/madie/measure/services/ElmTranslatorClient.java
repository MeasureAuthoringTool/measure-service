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
    return getElmJson(cql, measureModel, "Info", accessToken);
  }

  public ElmJson getElmJson(
      final String cql, String measureModel, String elmErrorSeverity, String accessToken) {
    try {
      // TODO CqlCompilorException is the sole reason for this project to rely on cql-t0-elm
      // dependency.. we could expose this value from madie-models instead
      URI uri =
          getElmJsonURI(measureModel, CqlCompilerException.ErrorSeverity.valueOf(elmErrorSeverity));
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

  public boolean hasOnlyWarnings(JsonNode errorExceptions) {
    for (JsonNode node : errorExceptions) {
      if ("Error".equalsIgnoreCase(node.get("errorSeverity").asText())) {
        return false;
      }
    }
    return true;
  }

  public boolean hasErrors(ElmJson elmJson) {
    if (elmJson == null) {
      return true;
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.readTree(elmJson.getJson());

      return (jsonNode.has("errorExceptions")
              && jsonNode.get("errorExceptions").size() > 0
              && !hasOnlyWarnings(jsonNode.get("errorExceptions")))
          || (jsonNode.has("externalErrors")
              && jsonNode.get("externalErrors").size() > 0
              && !hasOnlyWarnings(jsonNode.get("externalErrors")));
    } catch (Exception ex) {
      log.error("An error occurred parsing the response from the CQL-ELM translation service", ex);
      throw new CqlElmTranslationServiceException(
          "There was an error calling CQL-ELM translation service", ex);
    }
  }

  // overload method invocation so if we don't provide ErrorSeverity we assume that its info
  protected URI getElmJsonURI(String measureModel) {
    // TODO CqlCompilorException is the sole reason for this project to rely on cql-t0-elm
    // dependency.. we could expose this value from madie-models instead
    return getElmJsonURI(measureModel, CqlCompilerException.ErrorSeverity.Info);
  }

  // TODO CqlCompilorException is the sole reason for this project to rely on cql-t0-elm
  // dependency.. we could expose this value from madie-models instead
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
