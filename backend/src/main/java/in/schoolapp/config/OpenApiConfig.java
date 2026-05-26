package in.schoolapp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 spec + Swagger UI. Serves:
 * <ul>
 *   <li>Spec JSON at {@code /v3/api-docs}</li>
 *   <li>Swagger UI at {@code /swagger-ui/index.html}</li>
 * </ul>
 * Both are unauthenticated (see {@link SecurityConfig}) so dev can browse them without a token.
 * The {@code bearerAuth} scheme is registered so the "Authorize" button in Swagger UI lets
 * testers paste their JWT and try protected endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI schoolApiSpec() {
        return new OpenAPI()
            .info(new Info()
                .title("School Management System API")
                .version("0.1.0")
                .description("Multi-tenant school-management platform — attendance, fees, "
                    + "academics, communication, analytics, mobile sync, data export.")
                .contact(new Contact().name("School App Team").email("engineering@schoolapp.in"))
                .license(new License().name("Proprietary")))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste the access token from /auth/token/refresh "
                        + "or the OTP-verify response.")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
