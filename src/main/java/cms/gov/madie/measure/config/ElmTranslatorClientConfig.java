package cms.gov.madie.measure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Getter
@Configuration
public class ElmTranslatorClientConfig {

  @Value("${madie.cql-elm.service.qdm-base-url}")
  private String qdmCqlElmServiceBaseUrl;

  @Value("${madie.cql-elm.service.fhir-base-url}")
  private String fhirCqlElmServiceBaseUrl;

  @Value("${madie.cql-elm.service.elm-json-uri}")
  private String cqlElmServiceElmJsonUri;

  @Value("${madie.cql-elm.service.translator-version-uri}")
  private String cqlToElmTranslatorVersionUri;

  @Bean
  public RestTemplate elmTranslatorRestTemplate(
      ClientHttpRequestInterceptor bearerTokenInterceptor) {
    return new RestTemplateBuilder().additionalInterceptors(bearerTokenInterceptor).build();
  }
}
