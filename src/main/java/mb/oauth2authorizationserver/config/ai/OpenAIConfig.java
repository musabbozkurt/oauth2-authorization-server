package mb.oauth2authorizationserver.config.ai;

import com.google.genai.Client;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAIConfig {

    @Bean
    @ConditionalOnBean(OpenAiSdkChatModel.class)
    public ChatClient openAIChatClient(OpenAiSdkChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean
    @ConditionalOnBean(OllamaChatModel.class)
    public ChatClient ollamaChatClient(OllamaChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean
    @ConditionalOnBean(OpenAiSdkChatModel.class)
    public ChatClient gemma3ChatClient(OpenAiSdkChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean
    @ConditionalOnBean({OpenAiSdkChatModel.class, VectorStore.class})
    public ChatClient vectorStoreChatClient(OpenAiSdkChatModel chatModel, VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .build();
    }

    @Bean
    @ConditionalOnBean(GoogleGenAiChatModel.class)
    public ChatClient googleGenAiChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    @ConditionalOnBean(AnthropicChatModel.class)
    public ChatClient anthropicChatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder().ollamaApi(OllamaApi.builder().build()).build();
    }

    /*
     * Needed a longer timeout for the Anthropic Skills API demos
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);  // 30 seconds
        factory.setReadTimeout(600_000);    // 10 minutes
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    public GoogleGenAiChatModel googleGenAiChatModel(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
        return GoogleGenAiChatModel.builder()
                .genAiClient(Client.builder().apiKey(apiKey).build())
                .defaultOptions(GoogleGenAiChatOptions.builder().model("gemini-2.0-flash").build())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAIClient openAIClient(@Value("${spring.ai.openai-sdk.api-key}") String apiKey) {
        return OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }
}
