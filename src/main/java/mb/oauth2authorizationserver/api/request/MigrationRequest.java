package mb.oauth2authorizationserver.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for database migration configuration.
 * Allows specifying source and destination database connection details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Migration request containing source and destination database configurations")
public class MigrationRequest {

    @Schema(description = "Source database configuration (PostgreSQL)")
    private DatabaseConfig source;

    @Schema(description = "Destination database configuration (Oracle)")
    private DatabaseConfig destination;

    @Schema(
            example = """
                    {
                      "MB_MASTER": ["entity", "entity_address", "entity_relation"],
                      "MB_CATALOG": ["catalog_category_rate", "catalog_category_rate_history", "catalog_item_rate", "catalog_item_rate_history"],
                      "MB_POLICY": ["policy_revision", "policy_revision_status", "policy_revision_section", "policy_revision_section_approval", "policy_section_type"]
                    }
                    """,
            description = "Optional mapping from Oracle schema name to source table names. If provided, only mapped tables are migrated into mapped schemas and destination.schema is ignored for routing."
    )
    private Map<String, List<String>> tableSchemaMap;
}
