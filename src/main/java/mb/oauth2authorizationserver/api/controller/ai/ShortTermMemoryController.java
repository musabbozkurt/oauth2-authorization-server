package mb.oauth2authorizationserver.api.controller.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ShortTermMemoryController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public ShortTermMemoryController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.chatMemory = chatMemory;
    }

    @GetMapping("/short-term")
    public String memory(@RequestParam String prompt, @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    @GetMapping("/history")
    public List<Message> history(@RequestParam(defaultValue = "default") String conversationId) {
        return chatMemory.get(conversationId);
    }

    @GetMapping("/short-history")
    public List<String> shortHistory(@RequestParam(defaultValue = "default") String conversationId) {
        return chatMemory.get(conversationId).stream()
                .map(m -> m.getMessageType() + ": " + m.getText())
                .toList();
    }

    @GetMapping("/clear")
    public void clear(@RequestParam(defaultValue = "default") String conversationId) {
        chatMemory.clear(conversationId);
    }
}
