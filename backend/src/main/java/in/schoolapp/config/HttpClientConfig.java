package in.schoolapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Supplies a shared {@link RestClient.Builder} so outbound HTTP clients (WATI, Razorpay in
 * Slice 7, etc.) reuse a single JDK {@link java.net.http.HttpClient} connection pool and
 * identical default interceptors/timeouts.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
