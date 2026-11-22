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
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
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
@AutoConfigureRestTestClient
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
    private final RestTestClient restTestClient;

    @DynamicPropertySource
    static void ollamaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.ollama.base-url", ollamaContainer::getEndpoint);
    }

    @Test
    void longTerm_ShouldReturnReply_WhenUserIdIsValid() {
        EntityExchangeResult<String> response = restTestClient.get()
                .uri("/long-term?prompt={prompt}&userId={userId}", "Remember that my favourite colour is green.", "it-long-term-user")
                .exchange()
                .returnResult(String.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).isNotBlank();
    }

    @Test
    void longTerm_ShouldReturnBadRequest_WhenUserIdContainsInvalidCharacters() {
        EntityExchangeResult<String> response = restTestClient.get()
                .uri("/long-term?prompt={prompt}&userId={userId}", "Hello", "../etc")
                .exchange()
                .returnResult(String.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void vectorMemory_ShouldStoreAndSearchConversation_WhenChatIsCalled() {
        String userId = "it-vector-user";

        EntityExchangeResult<String> chat = restTestClient.get()
                .uri("/vector-memory?prompt={prompt}&userId={userId}", "My name is Maya and I live in Istanbul.", userId)
                .exchange()
                .returnResult(String.class);
        assertThat(chat.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(chat.getResponseBody()).isNotBlank();

        EntityExchangeResult<String[]> search = restTestClient.get()
                .uri("/vector-memory/search?query={query}&userId={userId}", "Where does the user live?", userId)
                .exchange()
                .returnResult(String[].class);
        assertThat(search.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(search.getResponseBody())
                .isNotNull()
                .isNotEmpty()
                .anySatisfy(entry -> assertThat(entry).contains("Istanbul"));
    }

    @Test
    void shortTerm_ShouldKeepHistoryPerConversation_AndClearIt() {
        String conversationId = "it-short-term-conversation";

        EntityExchangeResult<String> chat = restTestClient.get()
                .uri("/short-term?prompt={prompt}&conversationId={conversationId}", "My favourite fruit is mango.", conversationId)
                .exchange()
                .returnResult(String.class);
        assertThat(chat.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(chat.getResponseBody()).isNotBlank();

        EntityExchangeResult<String[]> history = restTestClient.get()
                .uri("/short-history?conversationId={conversationId}", conversationId)
                .exchange()
                .returnResult(String[].class);
        assertThat(history.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(history.getResponseBody())
                .isNotNull()
                .anySatisfy(entry -> assertThat(entry).contains("mango"));

        restTestClient.get()
                .uri("/clear?conversationId={conversationId}", conversationId)
                .exchange()
                .expectStatus().isOk();

        EntityExchangeResult<String[]> afterClear = restTestClient.get()
                .uri("/short-history?conversationId={conversationId}", conversationId)
                .exchange()
                .returnResult(String[].class);
        assertThat(afterClear.getResponseBody()).isEmpty();
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
