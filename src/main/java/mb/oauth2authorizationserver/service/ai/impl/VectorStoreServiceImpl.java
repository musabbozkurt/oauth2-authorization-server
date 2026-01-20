package mb.oauth2authorizationserver.service.ai.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.service.ai.VectorStoreService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final VectorStore vectorStore;
    private final ChatClient vectorStoreChatClient;

    @Value("classpath:/docs/the_beat_oct_2024_article.pdf")
    private Resource marketPdf;

    @Override
    public List<Document> getDocuments(String query) {
        List<Document> documents = List.of(
                new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
                new Document("The World is Big and Salvation Lurks Around the Corner"),
                new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));

        vectorStore.add(documents);

        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(5).build());
    }

    @Async
    @Override
    public void loadPdf() {
        var pdfReader = new ParagraphPdfDocumentReader(marketPdf);
        TextSplitter textSplitter = new TokenTextSplitter();
        vectorStore.accept(textSplitter.apply(pdfReader.get()));
        log.info("VectorStore loaded with PDF content");
    }

    @Override
    public String chat(String question) {
        return vectorStoreChatClient
                .prompt()
                .user(question)
                .call()
                .content();
    }
}
