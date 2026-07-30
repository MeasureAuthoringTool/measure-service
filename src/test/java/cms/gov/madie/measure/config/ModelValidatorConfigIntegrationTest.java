package cms.gov.madie.measure.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cms.gov.madie.measure.services.ModelValidator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class ModelValidatorConfigIntegrationTest {

  @Autowired private Map<String, ModelValidator> modelValidatorMap;

  @Test
  void modelValidatorMapContainsEveryModelValidatorBean() {
    assertTrue(modelValidatorMap.containsKey("qdmModelValidator"));
    assertTrue(modelValidatorMap.containsKey("qicoreModelValidator"));
    assertTrue(modelValidatorMap.containsKey("qicore6ModelValidator"));
    assertTrue(modelValidatorMap.containsKey("usqualitycore05ModelValidator"));
  }
}
