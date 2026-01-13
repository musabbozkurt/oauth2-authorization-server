package mb.oauth2authorizationserver.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO containing generated Oracle DDL and DCL scripts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing generated Oracle DDL/DCL scripts")
public class ScriptGenerationResponse {

    @Schema(description = "Number of tables processed")
    private int tableCount;

    @Schema(description = "Combined full script (DDL + DCL)")
    private String fullScript;

    @Schema(description = "Any warnings or notes generated during script generation")
    private List<String> warnings;
}
