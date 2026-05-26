package in.schoolapp.config;

import in.schoolapp.feature.FeatureFlagService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring Cache abstraction and provides an in-memory (ConcurrentMapCacheManager)
 * fallback. Production deployments will likely swap this for {@code RedisCacheManager}, but
 * the in-memory manager is sufficient for slice 1 — feature-flag lookups are cheap to recompute
 * if the cache cold-starts, and a per-JVM cache is correct since flags are immutable for the
 * duration of an override (which uses {@code @CacheEvict} to invalidate cluster-wide eventually
 * once Redis is wired).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // Declaring the cache region eagerly so misses don't auto-create unnamed regions.
        return new ConcurrentMapCacheManager(FeatureFlagService.CACHE_NAME);
    }
}
