package mb.oauth2authorizationserver.config.ai.advisor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Objects;

/**
 * Output-side guardrail. Lets the model answer, then scans the response for
 * secrets before it reaches the caller. An input filter has to anticipate every
 * way a user might ask; this only has to check whether the answer contains the
 * one string that must never leave.
 */
@Slf4j
@RequiredArgsConstructor
public class SecretLeakAdvisor implements CallAdvisor {

    private final List<String> secrets;

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);

        String reply = response.chatResponse() != null ? Objects.requireNonNull(response.chatResponse().getResult()).getOutput().getText() : null;
        boolean leaked = reply != null && secrets.stream().anyMatch(s -> reply.toLowerCase().contains(s.toLowerCase()));

        if (!leaked) {
            return response;
        }

        log.warn("Secret found in model response, replacing it before it reaches the caller");

        ChatResponse safe = new ChatResponse(List.of(new Generation(new AssistantMessage("I'm not able to share that."))));

        return ChatClientResponse.builder()
                .chatResponse(safe)
                .context(response.context())
                .build();
    }

    @Override
    public @NonNull String getName() {
        return "SecretLeakAdvisor";
    }

    @Override
    public int getOrder() {
        // Between the input filter (0) and the logger (200): the logger sits
        // closer to the model, so the console still shows the raw leak while
        // this advisor redacts it on the way back to the caller.
        return 100;
    }
}
