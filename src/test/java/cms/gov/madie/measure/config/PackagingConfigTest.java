package cms.gov.madie.measure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.services.PackageService;
import cms.gov.madie.measure.services.QicorePackageService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class PackagingConfigTest {

  @Mock private ApplicationContext context;
  @Mock private PackageService qdmPackageService;
  @Mock private QicorePackageService qicorePackageService;

  private PackagingConfig config;

  @BeforeEach
  void setUp() {
    config = new PackagingConfig();
  }

  @Test
  void testCreatePackageService() {
    // given set up mocks

    // when call method under test
    PackageService result = config.createPackageService(qicorePackageService);

    // when perform assertions
    assertSame(qicorePackageService, result);
  }

  @Test
  void testPackageServiceMapIncludesPrimaryBeanNamesAndAliases() {
    // given set up mocks
    when(context.getBeansOfType(PackageService.class))
        .thenReturn(
            Map.of(
                "qdmPackageService", qdmPackageService,
                "qicorePackageService", qicorePackageService));
    when(context.getAliases("qdmPackageService")).thenReturn(new String[] {"qdmAlias"});
    when(context.getAliases("qicorePackageService")).thenReturn(new String[0]);

    // when call method under test
    Map<String, PackageService> result = config.packageServiceMap(context);

    // when perform assertions
    assertEquals(3, result.size());
    assertSame(qdmPackageService, result.get("qdmPackageService"));
    assertSame(qdmPackageService, result.get("qdmAlias"));
    assertSame(qicorePackageService, result.get("qicorePackageService"));
  }
}
