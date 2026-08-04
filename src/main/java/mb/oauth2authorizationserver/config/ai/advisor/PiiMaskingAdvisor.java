package mb.oauth2authorizationserver.config.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

import java.util.regex.Pattern;

/**
 * Input-side guardrail. Masks PII(Personal Identifiable Information) in the user prompt before it is sent to the model.
 */
@Slf4j
public class PiiMaskingAdvisor implements CallAdvisor {

    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b");

    @Override
    public @NonNull ChatClientResponse adviseCall(ChatClientRequest request, @NonNull CallAdvisorChain chain) {
        String original = request.prompt().getUserMessage().getText();
        String masked = mask(original);

        if (!masked.equals(original)) {
            log.info("PII masked before outbound call");
        }

        ChatClientRequest updated = request.mutate()
                .prompt(request.prompt().augmentUserMessage(masked))
                .build();

        return chain.nextCall(updated);
    }

    @Override
    public @NonNull String getName() {
        return "PiiMaskingAdvisor";
    }

    @Override
    public int getOrder() {
        return 100; // higher precedence (lower number) than the logger, so the log shows MASKED text
    }

    private String mask(String text) {
        text = CARD.matcher(text).replaceAll("[CARD REDACTED]");
        text = SSN.matcher(text).replaceAll("[SSN REDACTED]");
        text = EMAIL.matcher(text).replaceAll("[EMAIL REDACTED]");
        return text;
    }
}
