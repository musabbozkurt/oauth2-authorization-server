package mb.oauth2authorizationserver.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request DTO for generating Oracle DDL and DCL scripts from PostgreSQL schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for generating Oracle DDL/DCL scripts from PostgreSQL database")
public class ScriptGenerationRequest {

    @Schema(description = "Source PostgreSQL database configuration")
    private DatabaseConfig source;

    @NotBlank
    @Schema(description = "Target Oracle schema name for generated scripts", example = "mb_oracle_schema")
    private String targetSchema;

    @Schema(description = "Edit role name for full CRUD access (SELECT, INSERT, UPDATE, DELETE). If not provided, derived from targetSchema (e.g., mb_oracle_schema -> MB_ORACLE_SCHEMA_EDIT_ROLE)", example = "MB_ORACLE_SCHEMA_EDIT_ROLE")
    private String editRoleName;

    @Schema(description = "View role name for read-only access (SELECT). If not provided, derived from targetSchema (e.g., mb_oracle_schema -> MB_ORACLE_SCHEMA_VIEW_ROLE)", example = "MB_ORACLE_SCHEMA_VIEW_ROLE")
    private String viewRoleName;

    @Builder.Default
    @Schema(
            description = "Users/applications to grant edit role to",
            example = """
                    ["myapp_user"]
                    """
    )
    private Set<String> editRoleUsers = Set.of("myapp_user");

    @Builder.Default
    @Schema(
            description = "Users/applications to grant view role to",
            example = """
                    ["myapp_user", "DWHUSER"]
                    """
    )
    private Set<String> viewRoleUsers = Set.of("myapp_user");
}
