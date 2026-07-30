package cms.gov.madie.measure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.services.ModelValidator;
import cms.gov.madie.measure.services.QiCoreModelValidator;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class ModelValidatorConfigTest {

  @Mock private ApplicationContext context;
  @Mock private ModelValidator qdmModelValidator;
  @Mock private QiCoreModelValidator qicoreModelValidator;

  private ModelValidatorConfig config;

  @BeforeEach
  void setUp() {
    config = new ModelValidatorConfig();
  }

  @Test
  void testCreateModelValidator() {
    // given set up mocks

    // when call method under test
    ModelValidator result = config.createModelValidator(qicoreModelValidator);

    // when perform assertions
    assertSame(qicoreModelValidator, result);
  }

  @Test
  void testModelValidatorMapIncludesPrimaryBeanNamesAndAliases() {
    // given set up mocks
    when(context.getBeansOfType(ModelValidator.class))
        .thenReturn(
            Map.of(
                "qdmModelValidator", qdmModelValidator,
                "qicoreModelValidator", qicoreModelValidator));
    when(context.getAliases("qdmModelValidator")).thenReturn(new String[] {"qdmAlias"});
    when(context.getAliases("qicoreModelValidator")).thenReturn(new String[0]);

    // when call method under test
    Map<String, ModelValidator> result = config.modelValidatorMap(context);

    // when perform assertions
    assertEquals(3, result.size());
    assertSame(qdmModelValidator, result.get("qdmModelValidator"));
    assertSame(qdmModelValidator, result.get("qdmAlias"));
    assertSame(qicoreModelValidator, result.get("qicoreModelValidator"));
  }
}
