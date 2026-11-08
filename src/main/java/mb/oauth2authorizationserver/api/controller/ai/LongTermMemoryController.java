package mb.oauth2authorizationserver.api.controller.ai;

import org.springaicommunity.agent.tools.AutoMemoryTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;

/// Reference: [Short Term vs Long Term Memory in Spring AI](https://www.youtube.com/watch?v=cai0knehSXM)
@RestController
public class LongTermMemoryController {

    private static final String SYSTEM = """
            You have long-term memory about this user: Markdown files you manage with the Memory tools.
            MEMORY.md is the index.
            
            Before you answer, call MemoryView on MEMORY.md, then view any file it lists that could help.
            If MEMORY.md does not exist, you have no memories of this user yet.
            
            When the user tells you something that will matter later:
            - Save it in a file, one topic per file, like travel.md. Update the file if it exists.
            - Add one line for the file to MEMORY.md: "- [Title](file.md) - one-line summary".
            """;

    private final ChatClient chatClient;
    private final Path root = Path.of("memories");

    public LongTermMemoryController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(SYSTEM)
                .build();
    }

    @GetMapping("/long-term")
    public String chat(@RequestParam String prompt, @RequestParam String userId) {
        if (!userId.matches("[a-z0-9-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId");
        }
        AutoMemoryTools tools = AutoMemoryTools.builder()
                .memoriesDir(root.resolve(userId))
                .build();
        return chatClient.prompt()
                .user(prompt)
                .tools(tools)
                .call()
                .content();
    }
}
