package mb.oauth2authorizationserver.api.controller;

import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.api.response.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chats/gemma3")
public class Gemma3ChatController {

    private final ChatClient gemma3ChatClient;

    @GetMapping("/query")
    public ResponseEntity<ChatResponse> query(@RequestParam String prompt) {
        try {
            return ResponseEntity.ok(
                    new ChatResponse(
                            gemma3ChatClient.prompt(prompt)
                                    .call()
                                    .content()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ChatResponse("Error occurred while processing request: " + e.getMessage()));
        }
    }
}
