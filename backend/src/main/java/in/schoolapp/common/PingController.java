package in.schoolapp.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Smoke-test endpoint for Slice 1. Verifies the full stack boots: Spring MVC routing, Jackson
 * serialization, CORS chain, security chain. Will stay as a harmless liveness probe in later
 * slices.
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.success(Map.of(
            "service", "school-management",
            "status", "ok",
            "timestamp", OffsetDateTime.now()
        ));
    }
}
