package mb.oauth2authorizationserver.service;

import mb.oauth2authorizationserver.api.request.MigrationRequest;
import mb.oauth2authorizationserver.api.request.ScriptGenerationRequest;
import mb.oauth2authorizationserver.api.response.ScriptGenerationResponse;

public interface OracleToolsService {

    ScriptGenerationResponse generateScripts(ScriptGenerationRequest request);

    void migrate(MigrationRequest request);
}
