package in.schoolapp.notification.push.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JVM-global push config. Per-tenant credentials override these via TenantProviderConfig.
 *
 * <p>For FCM: provide either {@code serviceAccountJson} (raw JSON content) or a
 * {@code serviceAccountPath} to a file on disk. The HTTP v1 sender prefers env-driven
 * inline JSON in container deployments.
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(
    @Pattern(regexp = "LOGGING|FCM|APNS|") String provider,
    Fcm fcm,
    Apns apns
) {
    public record Fcm(String projectId, String serviceAccountJson, String serviceAccountPath) {}
    public record Apns(String teamId, String keyId, String bundleId, String privateKey) {}
}
