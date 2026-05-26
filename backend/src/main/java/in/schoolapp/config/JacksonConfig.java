package in.schoolapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

/**
 * API JSON convention: camelCase keys, ISO-8601 dates, unknown fields ignored on read.
 * Matches the Phase 1 frontend plan's expected response shape.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .modulesToInstall(new JavaTimeModule())
            .featuresToDisable(WRITE_DATES_AS_TIMESTAMPS, FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    public ObjectMapper objectMapper(@Qualifier("jacksonCustomizer") Jackson2ObjectMapperBuilderCustomizer customizer) {
        var builder = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
        customizer.customize(builder);
        return builder.build();
    }
}
