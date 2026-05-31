package in.schoolapp.documents;

import in.schoolapp.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint behind the QR code printed on every issued document. A scan opens
 * {@code /api/v1/public/documents/verify?token=…}; we validate the HMAC and return the
 * document identity ({@code valid}, school, type, reference, issuedAt) — or {@code valid:false}
 * for a forged/garbled token. No authentication and no tenant data beyond what the token
 * itself already encodes.
 */
@RestController
@RequiredArgsConstructor
public class DocumentVerificationController {

    private final DocumentVerificationService verificationService;

    @GetMapping("/api/v1/public/documents/verify")
    public ApiResponse<Map<String, Object>> verify(@RequestParam("token") String token) {
        return ApiResponse.success(verificationService.verify(token).toResponse());
    }
}
