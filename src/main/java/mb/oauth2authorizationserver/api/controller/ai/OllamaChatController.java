package mb.oauth2authorizationserver.api.controller.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/ollama")
public class OllamaChatController {

    private final ChatClient ollamaChatClient;

    @GetMapping("/{question}")
    public String ollama(@PathVariable String question) {
        return ollamaChatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    @GetMapping("/{question}/stream")
    public Flux<String> ollamaStream(@PathVariable String question) {
        return ollamaChatClient.prompt()
                .user(question)
                .stream()
                .content();
    }
}
