package mb.oauth2authorizationserver.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
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
    @Schema(description = "Target Oracle schema name for legacy single-schema mode. Ignored when tableSchemaMap is provided.", example = "mb_oracle_schema")
    private String targetSchema;

    @Schema(description = "Edit role name for full CRUD access (SELECT, INSERT, UPDATE, DELETE). If not provided, derived from targetSchema (e.g., mb_oracle_schema -> MB_ORACLE_SCHEMA_EDIT_ROLE)", example = "MB_ORACLE_SCHEMA_EDIT_ROLE")
    private String editRoleName;

    @Schema(description = "View role name for read-only access (SELECT). If not provided, derived from targetSchema (e.g., mb_oracle_schema -> MB_ORACLE_SCHEMA_VIEW_ROLE)", example = "MB_ORACLE_SCHEMA_VIEW_ROLE")
    private String viewRoleName;

    @Schema(
            example = """
                    {
                      "MB_ORACLE_SCHEMA": ["myapp_user"]
                    }
                    """,
            description = "Users/applications to grant edit role to by Oracle schema. Key is Oracle schema name."
    )
    private Map<String, Set<String>> editRoleUsersBySchema;

    @Schema(
            example = """
                    {
                      "MB_ORACLE_SCHEMA": ["myapp_user", "DWHUSER"]
                    }
                    """,
            description = "Users/applications to grant view role to by Oracle schema. Key is Oracle schema name."
    )
    private Map<String, Set<String>> viewRoleUsersBySchema;

    @Schema(
            example = """
                    {
                      "MB_MASTER": ["entity", "entity_address", "entity_relation"],
                      "MB_CATALOG": ["catalog_category_rate", "catalog_category_rate_history", "catalog_item_rate", "catalog_item_rate_history"],
                      "MB_POLICY": ["policy_revision", "policy_revision_status", "policy_revision_section", "policy_revision_section_approval", "policy_section_type"]
                    }
                    """,
            description = "Optional mapping from Oracle schema name to source table names. If provided, only mapped tables are processed and targetSchema is ignored for routing."
    )
    private Map<String, List<String>> tableSchemaMap;
}
