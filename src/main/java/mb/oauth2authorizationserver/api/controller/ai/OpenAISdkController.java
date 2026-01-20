package mb.oauth2authorizationserver.api.controller.ai;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.WebSearchTool;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * NEW IN SPRING AI 2.0: Official OpenAI Java SDK Integration
 * <p>
 * Uses spring-ai-starter-model-openai-sdk which wraps the official OpenAI SDK.
 * Key benefits over the 1.x integration:
 * - Native Azure OpenAI & GitHub Models support
 * - Automatic API updates via SDK releases
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/openai")
public class OpenAISdkController {

    private final OpenAIClient openAIClient;

    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        return openAIClient
                .chat()
                .completions()
                .create(ChatCompletionCreateParams.builder().model(ChatModel.GPT_5_1).addUserMessage(message).build())
                .choices()
                .getFirst()
                .message()
                .content()
                .orElse("");
    }

    // ==================== RESPONSES API WITH WEB SEARCH ====================

    /**
     * Demonstrates OpenAI's Responses API with built-in web search.
     * <p>
     * This is NOT available through Spring AI's ChatClient - it requires
     * direct SDK access. The Responses API is OpenAI's new primary API with:
     * - Built-in web search (model searches the web automatically)
     * - 40-80% better caching vs Chat Completions
     * - Built-in tools (web search, file search, code interpreter)
     */
    @PostMapping("/responses/search")
    public WebSearchResponse searchWithResponses(@RequestBody Map<String, String> request) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .input(request.get("query"))
                .model(ChatModel.GPT_5_1)
                .addTool(WebSearchTool.builder()
                        .type(WebSearchTool.Type.WEB_SEARCH)
                        .build())
                .build();

        Response response = openAIClient.responses().create(params);

        var outputTexts = response.output().stream()
                .filter(ResponseOutputItem::isMessage)
                .flatMap(item -> item.asMessage().content().stream())
                .filter(ResponseOutputMessage.Content::isOutputText)
                .map(ResponseOutputMessage.Content::asOutputText)
                .toList();

        String text = outputTexts.stream()
                .map(ResponseOutputText::text)
                .collect(Collectors.joining());

        List<Citation> citations = outputTexts.stream()
                .flatMap(out -> out.annotations().stream())
                .filter(ResponseOutputText.Annotation::isUrlCitation)
                .map(ResponseOutputText.Annotation::asUrlCitation)
                .map(c -> new Citation(c.title(), c.url()))
                .toList();

        return new WebSearchResponse(text, citations);
    }

    public record WebSearchResponse(String answer, List<Citation> citations) {
    }

    public record Citation(String title, String url) {
    }
}
