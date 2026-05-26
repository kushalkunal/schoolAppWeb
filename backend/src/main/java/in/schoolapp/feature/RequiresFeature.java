package in.schoolapp.feature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method (or service entry point) as requiring a specific feature to be
 * enabled for the calling tenant. Enforced by {@link FeatureCheckAspect} at runtime.
 *
 * <pre>
 *   {@literal @}RequiresFeature(FeatureKey.FEE)
 *   public PaymentResponse quickCollect(...) { ... }
 * </pre>
 *
 * <p>If the feature is disabled for the current tenant, the method throws
 * {@code AppException(FEATURE_DISABLED)} — never reaching its body — and the global handler
 * returns {@code 403 FEATURE_DISABLED}.
 *
 * <p>Reading the current tenant from {@code TenantContext}, so this only works for endpoints
 * routed through {@code JwtAuthFilter}. Public/auth endpoints don't need (and shouldn't use)
 * this annotation.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {

    /** The feature key from {@link FeatureKey}. Must exist in the {@code features} catalog. */
    String value();
}
