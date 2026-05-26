package in.schoolapp.auth.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Feature flag that determines how new tenants can sign up and subsequently authenticate.
 * Bound from {@code app.signup.channel}. Defaults to PHONE for backward compatibility with
 * the Slice 2 behavior.
 */
@Validated
@ConfigurationProperties(prefix = "app.signup")
public record SignupProperties(@NotNull SignupChannel channel) {}
