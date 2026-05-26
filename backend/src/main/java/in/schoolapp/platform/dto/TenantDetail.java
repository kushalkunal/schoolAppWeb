package in.schoolapp.platform.dto;

import java.util.Map;

/**
 * Full detail bundle for one tenant — the platform-admin uses this on a single screen.
 */
public record TenantDetail(
    TenantSummary summary,
    Map<String, Boolean> features,        // effective feature flags
    Map<String, Long> usage,               // per-metric current counters
    Map<String, Long> limits               // per-metric plan ceilings (-1 = unlimited)
) {}
