package mb.oauth2authorizationserver.api.controller.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * NEW IN SPRING AI 2.0: Redis Chat Memory Repository
 * <p>
 * Demonstrates persistent conversation storage across sessions using Redis.
 * Features:
 * - Conversations persist across server restarts
 * - TTL support for automatic expiration
 * - Multiple concurrent conversations per user
 */
@RestController
@RequestMapping("/api/redis")
public class RedisChatMemoryController {

    private static final String CONVERSATION_ID = "conversationId";

    private final ChatClient chatClient;
    private final ChatMemoryRepository chatMemoryRepository;

    public RedisChatMemoryController(OpenAiSdkChatModel chatModel, ChatMemoryRepository chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;

        // Create chat memory with Redis repository
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();

        // Build ChatClient with memory advisor
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * Chat with persistent memory.
     * Messages are stored in Redis and persist across sessions.
     */
    @PostMapping("/chat/{conversationId}")
    public Map<String, Object> chat(@PathVariable String conversationId, @RequestBody ChatRequest request) {
        String response = chatClient.prompt()
                .user(request.message())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        return Map.of(CONVERSATION_ID, conversationId, "response", Objects.requireNonNullElse(response, "No response from chat API"));
    }

    /**
     * Get conversation history for a specific conversation.
     */
    @GetMapping("/history/{conversationId}")
    public Map<String, Object> getHistory(@PathVariable String conversationId) {
        List<Map<String, String>> history = chatMemoryRepository.findByConversationId(conversationId)
                .stream()
                .map(msg -> Map.of("type", msg.getMessageType().name(), "content", msg.getText()))
                .toList();

        return Map.of(CONVERSATION_ID, conversationId, "messageCount", history.size(), "messages", history);
    }

    /**
     * List all conversation IDs stored in Redis.
     */
    @GetMapping("/conversations")
    public Map<String, Object> listConversations() {
        var conversationIds = chatMemoryRepository.findConversationIds();

        return Map.of("count", conversationIds.size(), "conversationIds", conversationIds);
    }

    /**
     * Delete a conversation from Redis.
     */
    @DeleteMapping("/history/{conversationId}")
    public Map<String, Object> deleteHistory(@PathVariable String conversationId) {
        chatMemoryRepository.deleteByConversationId(conversationId);
        return Map.of(CONVERSATION_ID, conversationId, "deleted", true);
    }

    public record ChatRequest(String message) {
    }
}
