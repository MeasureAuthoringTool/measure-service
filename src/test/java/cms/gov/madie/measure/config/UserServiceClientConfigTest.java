package cms.gov.madie.measure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
class UserServiceClientConfigTest {

  private UserServiceClientConfig config;

  @BeforeEach
  void setUp() {
    config = new UserServiceClientConfig();
    ReflectionTestUtils.setField(config, "userServiceBaseUrl", "http://test-url");
  }

  @Test
  void testUserServiceRestTemplateCreated() {
    RestTemplate restTemplate = config.userServiceRestTemplate();

    assertThat(restTemplate, is(notNullValue()));
    assertThat(restTemplate.getInterceptors(), is(notNullValue()));
    assertThat(restTemplate.getInterceptors().size(), is(1));
  }

  @Test
  void testUserServiceBaseUrlProperty() {
    String baseUrl = (String) ReflectionTestUtils.getField(config, "userServiceBaseUrl");
    assertThat(baseUrl, is("http://test-url"));
  }
}
