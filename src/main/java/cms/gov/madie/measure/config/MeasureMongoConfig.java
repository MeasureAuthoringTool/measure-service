package cms.gov.madie.measure.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

@Configuration
public class MeasureMongoConfig {

  @Value("${spring.data.mongodb.uri}")
  private String madieMongoDbUrl;

  @Qualifier(value = "mongoDbFactory")
  public MongoDatabaseFactory mongoDatabaseFactory() {
    return new SimpleMongoClientDatabaseFactory(madieMongoDbUrl);
  }

  @Autowired private MongoMappingContext mongoMappingContext;

  @Bean
  public MongoConverter mongoConverter() throws Exception {
    DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDatabaseFactory());

    MappingMongoConverter mongoConverter =
        new MappingMongoConverter(dbRefResolver, mongoMappingContext);

    // Allow for dots in Mongo field names.
    // This knowingly affects only the cqlMeataData.codeSystemMap used by the Saved Codes UI.
    mongoConverter.preserveMapKeys(true);

    // customized converter
    mongoConverter.setCustomConversions(
        new MongoCustomConversions(
            Arrays.asList(new VersionConverter(), new StringOrganizationConverter())));

    return mongoConverter;
  }

  @Bean
  public GridFsTemplate gridFsTemplate(MongoDatabaseFactory mongoDbFactory) {
    return new GridFsTemplate(mongoDbFactory, new MongoTemplate(mongoDbFactory).getConverter());
  }
}
