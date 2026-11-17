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

    public static final int DEFAULT_CHUNK_WORKERS = 6;
    public static final boolean DEFAULT_REBUILD_PRIMARY_KEYS = true;

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

    @Schema(
            example = "6",
            description = "Optional number of parallel chunk workers used for large tables (single numeric primary key, > 1M rows). Defaults to 6, allowed range 1-24."
    )
    @Builder.Default
    private Integer chunkWorkers = DEFAULT_CHUNK_WORKERS;

    @Schema(
            example = "true",
            defaultValue = "true",
            description = "Drop the target primary key index of each large table before the load and rebuild it in parallel afterwards. Defaults to true."
    )
    @Builder.Default
    private Boolean rebuildPrimaryKeys = DEFAULT_REBUILD_PRIMARY_KEYS;
}
