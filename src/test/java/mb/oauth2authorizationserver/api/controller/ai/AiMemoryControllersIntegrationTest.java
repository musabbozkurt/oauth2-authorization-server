package mb.oauth2authorizationserver.api.controller.ai;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.base.BaseUnitTest;
import mb.oauth2authorizationserver.config.LgtmStackTestConfiguration;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.config.TestSecurityConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.ollama.OllamaContainer;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local-only HTTP integration tests for {@link LongTermMemoryController},
 * {@link VectorMemoryController} and {@link ShortTermMemoryController} backed by free Ollama
 * models via Testcontainers. Disabled in CI because pulling and running models is heavy.
 * <p>
 * The chat model is {@value #CHAT_MODEL} and the embedding model is {@value #EMBEDDING_MODEL}.
 */
@Slf4j
@Disabled("For local testing only")
@AutoConfigureTestRestTemplate
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {
                TestSecurityConfig.class,
                RedisTestConfiguration.class,
                LgtmStackTestConfiguration.class,
                AiMemoryControllersIntegrationTest.OllamaTestConfig.class
        },
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.ai.ollama.chat.model=" + AiMemoryControllersIntegrationTest.CHAT_MODEL,
                "spring.ai.ollama.embedding.options.model=" + AiMemoryControllersIntegrationTest.EMBEDDING_MODEL
        }
)
class AiMemoryControllersIntegrationTest extends BaseUnitTest {

    static final String CHAT_MODEL = "llama3.2:1b";
    static final String EMBEDDING_MODEL = "nomic-embed-text";

    private static final OllamaContainer ollamaContainer = new OllamaContainer("ollama/ollama:0.40.15")
            .withReuse(true);

    // Starts the container and pulls the models before the Spring context is created
    static {
        try {
            ollamaContainer.start();
            String output = ollamaContainer.execInContainer("ollama", "list").getStdout();
            log.info("Ollama models currently available:\n{}", output);

            for (String model : new String[]{CHAT_MODEL, EMBEDDING_MODEL}) {
                if (!output.contains(model)) {
                    log.info("Pulling {} model...", model);
                    ollamaContainer.execInContainer("ollama", "pull", model);
                    log.info("Model {} is ready", model);
                }
            }
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("Failed to prepare Ollama container", ex);
        }
    }

    @Autowired
    private TestRestTemplate testRestTemplate;

    @DynamicPropertySource
    static void ollamaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.ollama.base-url", ollamaContainer::getEndpoint);
    }

    @Test
    void longTerm_ShouldReturnReply_WhenUserIdIsValid() {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/long-term?prompt={prompt}&userId={userId}", String.class, "Remember that my favourite colour is green.", "it-long-term-user");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
    }

    @Test
    void longTerm_ShouldReturnBadRequest_WhenUserIdContainsInvalidCharacters() {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/long-term?prompt={prompt}&userId={userId}", String.class, "Hello", "../etc");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void vectorMemory_ShouldStoreAndSearchConversation_WhenChatIsCalled() {
        String userId = "it-vector-user";

        ResponseEntity<String> chat = testRestTemplate.getForEntity("/vector-memory?prompt={prompt}&userId={userId}", String.class, "My name is Maya and I live in Istanbul.", userId);
        assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chat.getBody()).isNotBlank();

        ResponseEntity<String[]> search = testRestTemplate.getForEntity("/vector-memory/search?query={query}&userId={userId}", String[].class, "Where does the user live?", userId);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(search.getBody())
                .isNotNull()
                .isNotEmpty()
                .anySatisfy(entry -> assertThat(entry).contains("Istanbul"));
    }

    @Test
    void shortTerm_ShouldKeepHistoryPerConversation_AndClearIt() {
        String conversationId = "it-short-term-conversation";

        ResponseEntity<String> chat = testRestTemplate.getForEntity("/short-term?prompt={prompt}&conversationId={conversationId}", String.class, "My favourite fruit is mango.", conversationId);
        assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chat.getBody()).isNotBlank();

        ResponseEntity<String[]> history = testRestTemplate.getForEntity("/short-history?conversationId={conversationId}", String[].class, conversationId);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody())
                .isNotNull()
                .anySatisfy(entry -> assertThat(entry).contains("mango"));

        ResponseEntity<Void> clear = testRestTemplate.getForEntity("/clear?conversationId={conversationId}", Void.class, conversationId);
        assertThat(clear.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String[]> afterClear = testRestTemplate.getForEntity("/short-history?conversationId={conversationId}", String[].class, conversationId);
        assertThat(afterClear.getBody()).isEmpty();
    }

    /**
     * Overrides {@code OpenAIConfig#chatClientBuilder} so the memory controllers talk to the
     * local Ollama chat model instead of OpenAI. Prototype scope gives each controller its own
     * builder, matching Spring AI's autoconfigured builder, so default advisors do not leak
     * between controllers.
     */
    @TestConfiguration
    static class OllamaTestConfig {

        @Bean
        @Primary
        @Scope("prototype")
        ChatClient.Builder chatClientBuilder(OllamaChatModel chatModel) {
            return ChatClient.builder(chatModel);
        }

        /*
         * Production OpenAIConfig#embeddingModel ignores spring.ai.ollama.base-url and always
         * targets localhost:11434, so this same-named bean points embeddings at the container.
         */
        @Bean
        @Primary
        EmbeddingModel embeddingModel() {
            return OllamaEmbeddingModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(ollamaContainer.getEndpoint()).build())
                    .defaultOptions(OllamaEmbeddingOptions.builder().model(EMBEDDING_MODEL).build())
                    .build();
        }
    }
}
