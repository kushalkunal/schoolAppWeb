package in.schoolapp.storage.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * File-storage configuration. Provider switch controls which {@link
 * in.schoolapp.storage.FileStorageService} bean is active.
 * <ul>
 *   <li>{@code LOCAL} — stores under {@code baseDir}, serves via the HTTP endpoint at
 *       {@code publicBaseUrl}. Default; safe for dev and single-node deploys.</li>
 *   <li>{@code S3}    — uses any S3-compatible endpoint (R2, B2, MinIO, AWS S3, DO Spaces,
 *       Wasabi). Free-tier options: Cloudflare R2 (10 GB + zero egress), Backblaze B2
 *       (10 GB + 1 GB/day egress), MinIO self-hosted.</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    @NotNull Provider provider,
    @Min(1) int presignTtlMinutes,
    LocalConfig local,
    S3Config s3
) {
    public enum Provider { LOCAL, S3 }

    /**
     * @param baseDir         filesystem root where files are written
     * @param publicBaseUrl   HTTP prefix that {@code FileController} serves from, e.g.
     *                        {@code http://localhost:8080/files}
     */
    public record LocalConfig(@NotBlank String baseDir, @NotBlank String publicBaseUrl) {}

    /**
     * @param endpoint        S3-compatible endpoint URL (leave blank for AWS default)
     * @param region          e.g. {@code "auto"} (R2), {@code "us-east-1"} (AWS), B2's region
     * @param bucket          target bucket — must already exist; app does not create it
     * @param accessKeyId     credentials
     * @param secretAccessKey credentials
     * @param publicBaseUrl   optional: if the bucket is public, return a direct URL instead of
     *                        presigning (e.g. {@code https://cdn.schoolapp.in})
     * @param forcePathStyle  some providers (MinIO) need path-style access rather than
     *                        virtual-hosted-style
     */
    public record S3Config(
        String endpoint, String region, @NotBlank String bucket,
        @NotBlank String accessKeyId, @NotBlank String secretAccessKey,
        String publicBaseUrl, boolean forcePathStyle
    ) {}
}
