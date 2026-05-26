package in.schoolapp.notification.push;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mobile push abstraction. Implementations: LOGGING (default), FCM, APNS.
 *
 * <p>Devices register their FCM / APNs tokens through the future
 * {@code /devices} endpoint (Slice 14 follow-up). Until that ships, callers pass
 * tokens explicitly to {@link #sendToTokens}.
 */
public interface PushNotifier {

    /**
     * Send a notification to a specific list of device tokens. Returns the number of
     * tokens that the provider accepted for delivery (not delivery confirmations —
     * those arrive asynchronously via webhook).
     *
     * @param schoolId  tenant whose credentials to resolve
     * @param tokens    device tokens (FCM / APNs); empty list → noop
     * @param title     visible title; truncated to platform limits by the impl
     * @param body      visible body
     * @param data      optional custom data payload (silent fields, deep-link target, etc.)
     */
    int sendToTokens(UUID schoolId, List<String> tokens, String title, String body, Map<String, String> data);

    /** Provider key — matches {@link in.schoolapp.tenantconfig.ProviderType}. */
    String providerName();
}
