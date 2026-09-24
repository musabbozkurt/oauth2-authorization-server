package mb.oauth2authorizationserver.api.request;

import mb.oauth2authorizationserver.model.enums.ModelTier;

import java.util.Map;

public record RoutingDecision(ModelTier tier, String model, double confidence, Map<String, Double> probabilities) {

}
