package in.schoolapp.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Canonical API response envelope.
 * <pre>
 *   { "success": true,  "data": {...}, "meta": {...} }
 *   { "success": false, "error": { "code": "...", "message": "...", "details": {} } }
 * </pre>
 * Null fields are omitted via {@link JsonInclude.Include#NON_NULL}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    Meta meta
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(String code, String message, Map<String, Object> details) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(Long total, Integer page, Integer limit, String nextCursor) {}

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, Meta meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code.name(), message, null), null);
    }

    public static ApiResponse<Void> error(ErrorCode code, String message, Map<String, Object> details) {
        return new ApiResponse<>(false, null, new ApiError(code.name(), message, details), null);
    }
}
