package in.schoolapp.common;

import lombok.Getter;

import java.util.Map;

/**
 * Single checked-at-the-boundary exception used by services. The global exception handler
 * converts this into the standard API error envelope with the correct HTTP status.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public AppException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, message, details, null);
    }

    public AppException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    public AppException(ErrorCode errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public static AppException notFound(ErrorCode code, String resource, Object id) {
        return new AppException(code, resource + " with id " + id + " was not found");
    }
}
