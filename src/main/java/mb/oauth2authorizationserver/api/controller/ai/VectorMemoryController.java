package mb.oauth2authorizationserver.api.controller.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class VectorMemoryController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public VectorMemoryController(ChatClient.Builder builder, EmbeddingModel embeddingModel) {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.chatClient = builder
                .defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(vectorStore)
                        .defaultTopK(5)
                        .build())
                .build();
    }

    @GetMapping("/vector-memory")
    public String chat(@RequestParam String prompt, @RequestParam String userId) {
        return chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                .call()
                .content();
    }

    @GetMapping("/vector-memory/search")
    public List<String> search(@RequestParam String query, @RequestParam String userId) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(5)
                .filterExpression("conversationId == '" + userId.replace("'", "") + "'")
                .build();
        return vectorStore.similaritySearch(request).stream()
                .map(d -> d.getMetadata().get("messageType") + ": " + d.getText() + " (score " + String.format("%.2f", d.getScore()) + ")")
                .toList();
    }
}
