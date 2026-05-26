package in.schoolapp.feature;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.JoinPoint;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Enforces {@link RequiresFeature} by intercepting method invocations and consulting
 * {@link FeatureFlagService}.
 *
 * <p>The aspect runs <em>before</em> the target method, so a disabled feature blocks the method
 * body from executing. The annotation can be applied at the method level (most common) or at the
 * type level (then every method inherits unless individually overridden); method-level wins on
 * conflict.
 *
 * <p>Resolution rules:
 * <ul>
 *   <li>{@code TenantContext.getTenantId() == null} → {@code UNAUTHORIZED} (some controller is
 *       gated but the request didn't pass through JwtAuthFilter — a bug).</li>
 *   <li>Feature not enabled → {@code FEATURE_DISABLED} (403).</li>
 *   <li>Feature enabled → method proceeds normally.</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class FeatureCheckAspect {

    private final FeatureFlagService featureFlagService;

    @Before("@annotation(in.schoolapp.feature.RequiresFeature) "
        + "|| @within(in.schoolapp.feature.RequiresFeature)")
    public void check(JoinPoint jp) {
        RequiresFeature ann = findAnnotation(jp);
        if (ann == null) return;  // shouldn't happen given the pointcut

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // Defensive — feature-gated code reached without an authenticated tenant.
            throw new AppException(ErrorCode.UNAUTHORIZED,
                "Authentication required for feature-gated endpoint");
        }
        featureFlagService.enforceEnabled(tenantId, ann.value());
    }

    /** Prefer the method-level annotation; fall back to the declaring-class one. */
    private RequiresFeature findAnnotation(JoinPoint jp) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        Method method = sig.getMethod();
        RequiresFeature ann = method.getAnnotation(RequiresFeature.class);
        if (ann != null) return ann;
        return method.getDeclaringClass().getAnnotation(RequiresFeature.class);
    }
}
