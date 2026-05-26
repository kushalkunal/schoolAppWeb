package in.schoolapp.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
    @Min(1) int ttlMinutes,
    @Min(1) int maxVerifyAttempts,
    @Min(1) int maxSendsPerWindow,
    @Min(1) int rateLimitWindowMinutes,
    @NotBlank @Size(min = 16) String hmacSecret
) {}
