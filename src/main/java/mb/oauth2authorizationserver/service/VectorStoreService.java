package mb.oauth2authorizationserver.service;

import org.springframework.ai.document.Document;

import java.util.List;

public interface VectorStoreService {

    List<Document> getDocuments(String query);

    void loadPdf();

    String chat(String question);
}
