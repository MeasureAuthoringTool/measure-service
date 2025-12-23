package cms.gov.madie.measure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
class UserServiceClientConfigTest {

  private UserServiceClientConfig config;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    config = new UserServiceClientConfig();
    objectMapper = new ObjectMapper();
    ReflectionTestUtils.setField(config, "userServiceBaseUrl", "http://test-url");
  }

  @Test
  void testUserServiceRestTemplateCreated() {
    RestTemplate restTemplate = config.userServiceRestTemplate(objectMapper);

    assertThat(restTemplate, is(notNullValue()));
    assertThat(restTemplate.getInterceptors(), is(notNullValue()));
    assertThat(restTemplate.getInterceptors().size(), is(1));
  }

  @Test
  void testUserServiceBaseUrlProperty() {
    String baseUrl = (String) ReflectionTestUtils.getField(config, "userServiceBaseUrl");
    assertThat(baseUrl, is("http://test-url"));
  }

  @Test
  void testRestTemplateHasMappingJackson2HttpMessageConverter() {
    RestTemplate restTemplate = config.userServiceRestTemplate(objectMapper);

    List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
    boolean hasMappingJackson2Converter =
        converters.stream()
            .anyMatch(converter -> converter instanceof MappingJackson2HttpMessageConverter);

    assertThat(hasMappingJackson2Converter, is(true));
  }

  @Test
  void testRestTemplateUsesProvidedObjectMapper() {
    RestTemplate restTemplate = config.userServiceRestTemplate(objectMapper);

    List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
    MappingJackson2HttpMessageConverter jacksonConverter =
        converters.stream()
            .filter(converter -> converter instanceof MappingJackson2HttpMessageConverter)
            .map(converter -> (MappingJackson2HttpMessageConverter) converter)
            .findFirst()
            .orElse(null);

    assertThat(jacksonConverter, is(notNullValue()));
    assertThat(jacksonConverter.getObjectMapper(), is(objectMapper));
  }
}
