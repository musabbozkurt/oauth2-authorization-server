package mb.oauth2authorizationserver.ai;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.ollama.OllamaContainer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@Disabled("For local testing only")
class SpringAILiveTest {

    @ServiceConnection
    private static final OllamaContainer ollamaContainer = new OllamaContainer("ollama/ollama:0.5.13")
            .withReuse(true);

    private static final ChatMemory chatMemory = MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).build();
    private static ChatClient chatClient;
    private static VectorStore vectorStore;

    @BeforeAll
    static void setUp() {
        ollamaContainer.start();

        var ollamaApi = new OllamaApi.Builder().baseUrl(ollamaContainer.getEndpoint()).build();

        // Only pull models if they don't exist
        ensureModelsAvailable();

        var chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(
                        OllamaChatOptions.builder()
                                .model(OllamaModel.MISTRAL)
                                .temperature(0.9)
                                .build())
                .build();
        chatClient = ChatClient.create(chatModel);

        vectorStore = SimpleVectorStore.builder(OllamaEmbeddingModel.builder().ollamaApi(ollamaApi).build()).build();

        Document bgDocument = new Document("The World is Big", Map.of("country", "Bulgaria"));
        Document nlDocument = new Document("The World is Big", Map.of("country", "Netherlands"));

        vectorStore.add(List.of(bgDocument, nlDocument));
    }

    private static void ensureModelsAvailable() {
        try {
            // Check if models exist, if not pull them
            var result = ollamaContainer.execInContainer("ollama", "list");
            String output = result.getStdout();


            if (!output.contains("mxbai-embed-large")) {
                log.info("Pulling mxbai-embed-large model...");
                ollamaContainer.execInContainer("ollama", "pull", "mxbai-embed-large");
            }

            if (!output.contains("qwen2.5:3b")) {
                log.info("Pulling qwen2.5:3b model...");
                ollamaContainer.execInContainer("ollama", "pull", "qwen2.5:3b");
            }

            log.info("Models are ready");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to ensure models are available", e);
        }
    }

    @Test
    void givenMessageChatMemoryAdvisor_WhenAskingChatToIncrementTheResponseWithNewName_ThenNamesFromTheChatHistoryExistInResponse() {
        MessageChatMemoryAdvisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        String responseContent = chatClient.prompt()
                .user("Add this name to a list and return all the values: Bob")
                .advisors(chatMemoryAdvisor)
                .call()
                .content();

        assertThat(responseContent)
                .contains("Bob");

        responseContent = chatClient.prompt()
                .user("Add this name to a list and return all the values: John")
                .advisors(chatMemoryAdvisor)
                .call()
                .content();

        assertThat(responseContent)
                .contains("Bob")
                .contains("John");

        responseContent = chatClient.prompt()
                .user("Add this name to a list and return all the values: Anna")
                .advisors(chatMemoryAdvisor)
                .call()
                .content();

        assertThat(responseContent)
                .contains("Bob")
                .contains("John")
                .contains("Anna");
    }

    @Test
    void givenPromptChatMemoryAdvisor_WhenAskingChatToIncrementTheResponseWithNewName_ThenNamesFromTheChatHistoryExistInResponse() {
        PromptChatMemoryAdvisor chatMemoryAdvisor = PromptChatMemoryAdvisor.builder(chatMemory).build();

        String responseContent = chatClient.prompt()
                .user("Add this name to a list and return all the values: Bob")
                .advisors(chatMemoryAdvisor)
                .call()
                .content();

        assertThat(responseContent)
                .contains("Bob");

        responseContent = chatClient.prompt()
                .user("Add this name to a list and return all the values: John")
                .advisors(chatMemoryAdvisor)
                .call()
                .content();

        assertThat(responseContent)
                .contains("Bob")
                .contains("John");

        responseContent = chatClient.prompt()
                .user("Add this name to a list and return all the values: Anna")
                .advisors(chatMemoryAdvisor)
                .call()
                .content();

        assertThat(responseContent)
                .contains("Bob")
                .contains("John")
                .contains("Anna");
    }

    @Test
    void givenSafeGuardAdvisor_WhenSendPromptWithSensitiveWord_ThenExpectedMessageShouldBeReturned() {
        List<String> forbiddenWords = List.of("Word2");
        SafeGuardAdvisor safeGuardAdvisor = new SafeGuardAdvisor(forbiddenWords);

        String responseContent = chatClient.prompt()
                .user("Please split the 'Word2' into characters")
                .advisors(safeGuardAdvisor)
                .call()
                .content();

        assertThat(responseContent)
                .contains("I'm unable to respond to that due to sensitive content");
    }

    @Test
    void givenCustomLoggingAdvisor_WhenSendPrompt_ThenPromptTextAndResponseShouldBeLogged() {
        CustomLoggingAdvisor customLoggingAdvisor = new CustomLoggingAdvisor();

        String responseContent = chatClient.prompt()
                .user("Count from 1 to 10")
                .advisors(customLoggingAdvisor)
                .call()
                .content();

        assertThat(responseContent)
                .contains("1")
                .contains("10");
    }

    @Test
    void givenQuestionAnswerAdvisor_WhenSearchingWithFilters_ThenShouldReturnMatchingDocuments() {
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore).build();

        String responseContent = chatClient.prompt()
                .user("How many documents with 'The World is Big' text are in the vector store?")
                .advisors(questionAnswerAdvisor)
                .call()
                .content();

        assertThat(responseContent)
                .contains("2");

        Filter.Expression filterExpression = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("country"),
                new Filter.Value("Bulgaria")
        );

        SearchRequest request = SearchRequest.builder()
                .query("World")
                .filterExpression("country == 'Bulgaria'")
                .build();
        List<Document> results = vectorStore.similaritySearch(request);

        SearchRequest requestWithFilterExpression = SearchRequest.builder()
                .query("World")
                .filterExpression(filterExpression)
                .build();
        List<Document> resultsWithFilterExpression = vectorStore.similaritySearch(requestWithFilterExpression);

        assertThat(results).hasSize(1);
        assertThat(resultsWithFilterExpression).hasSize(1);
    }
}

@Slf4j
class CustomLoggingAdvisor implements CallAdvisor {

    @NonNull
    @Override
    public ChatClientResponse adviseCall(@NonNull ChatClientRequest advisedRequest, CallAdvisorChain chain) {
        this.observeBefore(advisedRequest);
        ChatClientResponse advisedResponse = chain.nextCall(advisedRequest);
        this.observeAfter(advisedResponse);
        return advisedResponse;
    }

    @NonNull
    @Override
    public String getName() {
        return "CustomLoggingAdvisor";
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }

    private void observeBefore(ChatClientRequest advisedRequest) {
        log.info(advisedRequest.prompt().toString());
    }

    private void observeAfter(ChatClientResponse advisedResponse) {
        log.info(advisedResponse.chatResponse() != null ? advisedResponse.chatResponse().getResult().getOutput().getText() : null);
    }
}
