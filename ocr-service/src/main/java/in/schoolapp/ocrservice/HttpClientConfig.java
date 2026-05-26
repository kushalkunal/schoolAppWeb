package in.schoolapp.ocrservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a {@link RestClient.Builder} bean (Spring Boot auto-configures one only when
 * webflux is on the classpath; we're on web-mvc, so declare explicitly).
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
