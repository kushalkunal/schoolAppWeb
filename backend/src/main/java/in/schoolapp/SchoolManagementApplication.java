package in.schoolapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Bootstrap class. Enable annotations (@EnableJpaAuditing, @EnableAsync, @EnableScheduling) live
 * on their respective @Configuration classes in {@code config/} — keeping them off the bootstrap
 * class lets slice tests (@WebMvcTest, @DataJpaTest) avoid loading concerns they don't need.
 * <p>
 * {@code @ConfigurationPropertiesScan} picks up @ConfigurationProperties records across modules
 * without each module having to register them via @EnableConfigurationProperties.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("in.schoolapp")
public class SchoolManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolManagementApplication.class, args);
    }
}
