package mb.oauth2authorizationserver.api.controller.ai;

import mb.oauth2authorizationserver.api.request.RoutingDecision;
import mb.oauth2authorizationserver.service.ModelRouterService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// References:
///
/// [Spring AI TypeSafe Is Here: Build a Model Router From Scratch](https://www.youtube.com/watch?v=_5V6sJxRgqk)
///
/// [Spring AI and TypeSafe Jev: Fast, Cheap, Structured Decisions](https://spring.io/blog/2026/09/21/spring-ai-typesafe-structured-judgment)
@RestController
@RequestMapping("/api/model-routers")
public class ModelRouterChatController {

    private final ModelRouterService modelRouterService;
    private final ChatClient chatClient;

    public ModelRouterChatController(ModelRouterService modelRouterService, ChatClient.Builder chatClientBuilder) {
        this.modelRouterService = modelRouterService;
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/chat")
    public ChatResult chat(@RequestBody ChatRequest request) {
        RoutingDecision decision = this.modelRouterService.route(request.prompt());
        String answer = this.chatClient.prompt()
                .user(request.prompt())
                .options(OpenAiChatOptions.builder().model(decision.model()))
                .call()
                .content();
        return new ChatResult(decision, answer);
    }

    public record ChatRequest(String prompt) {
    }

    public record ChatResult(RoutingDecision routing, String answer) {
    }
}
