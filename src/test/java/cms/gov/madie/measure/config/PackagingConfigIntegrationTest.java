package cms.gov.madie.measure.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cms.gov.madie.measure.services.PackageService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class PackagingConfigIntegrationTest {

  @Autowired private Map<String, PackageService> packageServiceMap;

  @Test
  void packageServiceMapContainsEveryPackageServiceBean() {
    assertTrue(packageServiceMap.containsKey("qdmPackageService"));
    assertTrue(packageServiceMap.containsKey("qicorePackageService"));
    assertTrue(packageServiceMap.containsKey("qicore6PackageService"));
    assertTrue(packageServiceMap.containsKey("usqualitycore05PackageService"));
  }
}
