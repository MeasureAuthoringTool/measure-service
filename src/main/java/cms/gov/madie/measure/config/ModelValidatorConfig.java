package cms.gov.madie.measure.config;

import cms.gov.madie.measure.services.ModelValidator;
import cms.gov.madie.measure.services.QiCoreModelValidator;
import cms.gov.madie.measure.services.ServiceConstants;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelValidatorConfig {

  @Bean(name = ServiceConstants.USQUALITYCORE_05_VALIDATOR)
  public ModelValidator createModelValidator(
      @Qualifier(ServiceConstants.QICORE_VALIDATOR) QiCoreModelValidator qicoreModelValidator) {
    return qicoreModelValidator;
  }

  @Bean
  public Map<String, ModelValidator> modelValidatorMap(ApplicationContext context) {
    Map<String, ModelValidator> finalMap = new HashMap<>();
    Map<String, ModelValidator> primaryBeans = context.getBeansOfType(ModelValidator.class);

    for (Map.Entry<String, ModelValidator> entry : primaryBeans.entrySet()) {
      String primaryName = entry.getKey();
      ModelValidator beanInstance = entry.getValue();

      finalMap.put(primaryName, beanInstance);

      for (String alias : context.getAliases(primaryName)) {
        finalMap.put(alias, beanInstance);
      }
    }

    return finalMap;
  }
}
