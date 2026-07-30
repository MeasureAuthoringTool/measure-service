package cms.gov.madie.measure.config;

import cms.gov.madie.measure.services.PackageService;
import cms.gov.madie.measure.services.QicorePackageService;
import cms.gov.madie.measure.services.ServiceConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class PackagingConfig {

  @Bean(
      name = {
          ServiceConstants.USQUALITYCORE_05_PACKAGE_SERVICE
      }) // add future US Quality Core versions here unless they have their own packaging rules
  public PackageService createPackageService(@Autowired QicorePackageService qicorePackageService) {
    return qicorePackageService;
  }

  @Bean
  public Map<String, PackageService> packageServiceMap(ApplicationContext context) {
    Map<String, PackageService> finalMap = new HashMap<>();

    // 1. Get all beans of the target type (this gives you primary names)
    Map<String, PackageService> primaryBeans = context.getBeansOfType(PackageService.class);

    for (Map.Entry<String, PackageService> entry : primaryBeans.entrySet()) {
      String primaryName = entry.getKey();
      PackageService beanInstance = entry.getValue();

      // 2. Map the primary name to the instance
      finalMap.put(primaryName, beanInstance);

      // 3. Find and map all aliases pointing to this specific primary name
      String[] aliases = context.getAliases(primaryName);
      for (String alias : aliases) {
        finalMap.put(alias, beanInstance);
      }
    }

    return finalMap;
  }
}
