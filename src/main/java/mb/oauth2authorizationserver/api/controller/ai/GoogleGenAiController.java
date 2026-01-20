package mb.oauth2authorizationserver.api.controller.ai;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * NEW IN SPRING AI 2.0: Google GenAI (Gemini) Integration
 * <p>
 * Uses spring-ai-starter-model-google-genai for easy Gemini access.
 * Features in 2.0:
 * - Gemini 2.0 Flash model support
 * - Safety ratings in response metadata
 * - Thought signatures for function calling (Gemini 3 Pro)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/google")
@ConditionalOnBean(name = "googleGenAiChatClient")
public class GoogleGenAiController {

    private final ChatClient googleGenAiChatClient;

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String response = googleGenAiChatClient.prompt()
                .user(request.message())
                .call()
                .content();

        if (StringUtils.isBlank(response)) {
            return Map.of("response", "No response from Google GenAI chat API");
        }

        return Map.of("response", response);
    }

    /**
     * Chat with safety ratings included in response.
     * NEW in 2.0: Safety ratings metadata available.
     */
    @PostMapping("/chat/safe")
    public SafeResponse chatWithSafety(@RequestBody ChatRequest request) {
        var response = googleGenAiChatClient.prompt()
                .user(request.message())
                .call()
                .chatResponse();

        if (response == null) {
            return new SafeResponse("No response from Google GenAI chat safe API", List.of());
        }

        String content = response.getResult().getOutput().getText();

        // Extract safety ratings from metadata if available
        var safetyRatings = response.getMetadata().get("safetyRatings");

        return new SafeResponse(content, safetyRatings != null ? safetyRatings : List.of());
    }

    /**
     * NEW in 2.0: Thinking mode with thinkingBudget support.
     * Uses Gemini 2.5 Pro with extended thinking for complex reasoning tasks.
     * thinkingBudget controls token allocation: -1 for dynamic, 0 to disable, or specific token count.
     */
    @PostMapping("/chat/think")
    public ThinkingResponse chatWithThinking(@RequestBody ThinkingRequest request) {
        // Default to dynamic thinking (-1), or use provided budget
        int budget = request.thinkingBudget() != null ? request.thinkingBudget() : -1;

        var options = GoogleGenAiChatOptions.builder()
                .model("gemini-2.5-pro")
                .thinkingBudget(budget)
                .includeThoughts(true)
                .build();

        var response = googleGenAiChatClient.prompt()
                .user(request.message())
                .options(options)
                .call()
                .chatResponse();

        if (response == null) {
            return new ThinkingResponse("No response from Google GenAI API", null, budget);
        }

        String content = response.getResult().getOutput().getText();

        // Extract thinking/reasoning from metadata if available
        var thoughts = response.getMetadata().get("thoughts");

        return new ThinkingResponse(content, thoughts != null ? thoughts.toString() : null, budget);
    }

    public record ChatRequest(String message) {
    }

    public record ThinkingRequest(String message, Integer thinkingBudget) {
    }

    public record SafeResponse(String response, Object safetyRatings) {
    }

    public record ThinkingResponse(String response, String thoughts, int thinkingBudget) {
    }
}
