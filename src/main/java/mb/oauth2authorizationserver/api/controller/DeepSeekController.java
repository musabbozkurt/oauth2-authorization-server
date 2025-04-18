package mb.oauth2authorizationserver.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/deep-seek")
public class DeepSeekController {

    private final ChatClient openAIChatClient;

    @GetMapping("/{question}")
    public String deepSeek(@PathVariable String question) {
        return openAIChatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    @GetMapping("/{question}/stream")
    public Flux<String> deepSeekStream(@PathVariable String question) {
        return openAIChatClient.prompt()
                .user(question)
                .stream()
                .content();
    }
}
