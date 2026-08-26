package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.ElmTranslatorClientConfig;
import cms.gov.madie.measure.exceptions.CqlElmTranslationServiceException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.ElmJson;
import org.apache.commons.lang3.Strings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
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

  public ElmJson getElmJson(final String cql, String measureModel) {
    return getElmJson(cql, measureModel, "Info");
  }

  public ElmJson getElmJson(final String cql, String measureModel, String elmErrorSeverity) {
    try {
      // TODO CqlCompilerException is the sole reason for this project to rely on cql-to-elm
      // dependency.. we could expose this value from madie-models instead
      URI uri =
          getElmJsonURI(measureModel, CqlCompilerException.ErrorSeverity.valueOf(elmErrorSeverity));
      HttpEntity<String> cqlEntity = new HttpEntity<>(cql);
      return elmTranslatorRestTemplate
          .exchange(uri, HttpMethod.PUT, cqlEntity, ElmJson.class)
          .getBody();
    } catch (Exception ex) {
      log.error("An error occurred calling the CQL to ELM translation service", ex);
      throw new CqlElmTranslationServiceException(
          "There was an error calling CQL-ELM translation service", ex);
    }
  }

  /**
   * Fetches the CQL to ELM translator version. Result is cached for up to 1 hour to avoid repeated
   * network calls for a value that rarely changes.
   *
   * @return The version string of the CQL to ELM translator, or null if an error occurs.
   */
  @Cacheable(value = "translatorVersion", key = "#model")
  public String getCqlToElmTranslatorVersion(String model) {
    var isQdm = Strings.CS.equals(model, ModelType.QDM_5_6.getValue());
    String baseUrl =
        isQdm
            ? elmTranslatorClientConfig.getQdmCqlElmServiceBaseUrl()
            : elmTranslatorClientConfig.getFhirCqlElmServiceBaseUrl();
    try {
      URI uri =
          UriComponentsBuilder.fromUriString(
                  baseUrl + elmTranslatorClientConfig.getCqlToElmTranslatorVersionUri())
              .queryParam("draft", true)
              .build()
              .encode()
              .toUri();
      return elmTranslatorRestTemplate.getForObject(uri, String.class);
    } catch (Exception ex) {
      log.error("An error occurred while fetching CQL-ELM translator version", ex);
      return null;
    }
  }

  /**
   * Extracts the translatorVersion directly from the ELM JSON string stored in the database. The
   * version is found in the library.annotation array where type is "CqlToElmInfo".
   *
   * @param elmJsonString The ELM JSON string stored in the database.
   * @return The translator version string, or null if not found or an error occurs.
   */
  public String getTranslatorVersionFromElmJson(String elmJsonString) {
    if (StringUtils.isBlank(elmJsonString)) {
      return null;
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode rootNode = mapper.readTree(elmJsonString);
      JsonNode annotationNode = rootNode.path("library").path("annotation");
      if (annotationNode.isArray()) {
        for (JsonNode entry : annotationNode) {
          if ("CqlToElmInfo".equals(entry.path("type").asString(null))) {
            return entry.path("translatorVersion").asString(null);
          }
        }
      }
    } catch (Exception ex) {
      log.error("An error occurred extracting translatorVersion from ELM JSON", ex);
    }
    return null;
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
    // TODO CqlCompilerException is the sole reason for this project to rely on cql-to-elm
    // dependency.. we could expose this value from madie-models instead
    return getElmJsonURI(measureModel, CqlCompilerException.ErrorSeverity.Info);
  }

  // TODO CqlCompilerException is the sole reason for this project to rely on cql-to-elm
  // dependency.. we could expose this value from madie-models instead
  protected URI getElmJsonURI(
      String measureModel, CqlCompilerException.ErrorSeverity errorSeverity) {
    var isQdm = StringUtils.equals(measureModel, ModelType.QDM_5_6.getValue());
    String baseUrl =
        isQdm
            ? elmTranslatorClientConfig.getQdmCqlElmServiceBaseUrl()
            : elmTranslatorClientConfig.getFhirCqlElmServiceBaseUrl();
    URI uri;
    if (!isQdm) {
      uri =
          UriComponentsBuilder.fromUriString(
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
}
