package mb.oauth2authorizationserver.api.controller.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.models.beta.files.FileMetadata;
import com.anthropic.models.beta.files.FileRetrieveMetadataParams;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicCitationDocument;
import org.springframework.ai.anthropic.AnthropicSkill;
import org.springframework.ai.anthropic.AnthropicSkillsResponseHelper;
import org.springframework.ai.anthropic.Citation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * NEW IN SPRING AI 2.0: Anthropic Claude Advanced Features
 * <p>
 * Demonstrates two new Claude capabilities:
 * - Citations API: Get source references in responses
 * - Skills/Files API: Generate documents (Excel, PowerPoint, Word, PDF)
 * <p>
 * <a href="https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html#_citations">...</a>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/anthropic")
@ConditionalOnBean(name = {"anthropicChatClient", "anthropicClient"})
public class AnthropicController {

    private final AnthropicChatModel chatModel;
    private final ChatClient anthropicChatClient;
    private final AnthropicClient anthropicClient; // official SDK bean, replaces AnthropicApi

    /**
     * NEW IN 2.0: Citations API - Claude references specific parts of your document.
     * Useful for fact-checking and source verification.
     */
    @PostMapping("/citations")
    public CitationsResponse citations(@RequestBody CitationRequest request) {
        AnthropicCitationDocument document = AnthropicCitationDocument.builder()
                .plainText(request.document())
                .title(request.title())
                .citationsEnabled(true)
                .build();

        ChatResponse response = anthropicChatClient.prompt()
                .user(request.question())
                .options(AnthropicChatOptions.builder()
                        .model("claude-opus-4-5")
                        .citationDocuments(document))
                .call()
                .chatResponse();

        if (response == null) {
            return new CitationsResponse("No response from Anthropic API", List.of());
        }

        return new CitationsResponse(Optional.ofNullable(response.getResult()).map(Generation::getOutput).map(AssistantMessage::getText).orElse(null), extractCitations(response));
    }

    /**
     * NEW IN 2.0: Skills API - Claude generates downloadable documents.
     * Supports: excel, powerpoint, word, pdf
     */
    @PostMapping("/skills/{type}")
    public SkillResponse generateDocument(@PathVariable String type, @RequestBody SkillRequest request) {
        AnthropicSkill skill = switch (type.toLowerCase()) {
            case "excel" -> AnthropicSkill.XLSX;
            case "powerpoint" -> AnthropicSkill.PPTX;
            case "word" -> AnthropicSkill.DOCX;
            case "pdf" -> AnthropicSkill.PDF;
            default -> throw new IllegalArgumentException("Supported types: excel, powerpoint, word, pdf");
        };

        ChatResponse response = chatModel.call(
                new Prompt(
                        request.prompt(),
                        AnthropicChatOptions.builder()
                                .model("claude-sonnet-4-5")
                                .maxTokens(16384)
                                .skill(skill)
                                .build()
                )
        );

        return new SkillResponse(Optional.ofNullable(response.getResult()).map(Generation::getOutput).map(AssistantMessage::getText).orElse(null), extractFileIds(response), type);
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId) throws IOException {
        // Metadata retrieval
        FileMetadata metadata = anthropicClient.beta().files()
                .retrieveMetadata(FileRetrieveMetadataParams.builder().fileId(fileId).build());

        // Binary download — method is download(), not retrieveContent()
        try (HttpResponse httpResponse = anthropicClient.beta().files().download(fileId)) {
            byte[] content = httpResponse.body().readAllBytes();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(metadata.filename()))
                    .contentType(MediaType.parseMediaType(metadata.mimeType()))
                    .body(content);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Citation> extractCitations(ChatResponse response) {
        Object citations = response.getMetadata().get("citations");
        return citations instanceof List ? (List<Citation>) citations : List.of();
    }

    private List<String> extractFileIds(ChatResponse response) {
        return AnthropicSkillsResponseHelper.extractFileIds(response);
    }

    public record CitationRequest(String document, String title, String question) {
    }

    public record CitationsResponse(String response, List<Citation> citations) {
    }

    public record SkillRequest(String prompt) {
    }

    public record SkillResponse(String response, List<String> fileIds, String type) {
    }
}
