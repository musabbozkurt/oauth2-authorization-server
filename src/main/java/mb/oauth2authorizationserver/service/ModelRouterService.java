package mb.oauth2authorizationserver.service;

import mb.oauth2authorizationserver.api.request.RoutingDecision;

public interface ModelRouterService {

    RoutingDecision route(String prompt);
}
