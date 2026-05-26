package in.schoolapp.ai.chatbot;

import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ai")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.AI_CHATBOT)
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(
        @PathVariable UUID tenantId,
        @Valid @RequestBody ChatRequest req
    ) {
        String reply = chatbotService.answer(tenantId, req.query());
        return ApiResponse.success(Map.of("reply", reply));
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String query) {}
}
