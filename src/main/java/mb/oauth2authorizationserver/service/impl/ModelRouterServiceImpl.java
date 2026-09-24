package mb.oauth2authorizationserver.service.impl;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.api.request.RoutingDecision;
import mb.oauth2authorizationserver.model.enums.ModelTier;
import mb.oauth2authorizationserver.service.ModelRouterService;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class ModelRouterServiceImpl implements ModelRouterService {

    private final TypeSafeClient typeSafeClient;
    private final Choice tierChoice;

    public ModelRouterServiceImpl(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
        Choice.Builder choice = Choice
                .builder()
                .instructions("Which model tier is the cheapest one that can still answer this prompt well? Prefer the cheaper tier unless the prompt clearly needs more capability.");
        for (ModelTier tier : ModelTier.values()) {
            choice.option(tier.name(), tier.getDescription());
        }
        this.tierChoice = choice.build();
    }

    @Override
    public RoutingDecision route(String prompt) {
        ChoiceAnswer answer = this.typeSafeClient.systemOne(prompt, Map.of("tier", this.tierChoice)).choice("tier");
        ModelTier tier = ModelTier.valueOf(answer.value());
        log.info("Routing to {} with confidence {}", tier.getModelId(), answer.confidence());
        return new RoutingDecision(tier, tier.getModelId(), answer.confidence(), answer.probabilities());
    }
}
