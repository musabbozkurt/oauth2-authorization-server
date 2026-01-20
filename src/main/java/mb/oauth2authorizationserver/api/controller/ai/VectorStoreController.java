package mb.oauth2authorizationserver.api.controller.ai;

import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.service.ai.VectorStoreService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/vector-stores")
public class VectorStoreController {

    private final VectorStoreService vectorStoreService;

    @GetMapping("/documents")
    public List<Document> getDocuments(@RequestParam(defaultValue = "Spring AI") String query) {
        return vectorStoreService.getDocuments(query);
    }

    @GetMapping("/load-pdf")
    public void loadPdf() {
        vectorStoreService.loadPdf();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "What is Spring AI?") String question) {
        return vectorStoreService.chat(question);
    }
}
