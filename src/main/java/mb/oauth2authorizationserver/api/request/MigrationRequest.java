package mb.oauth2authorizationserver.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
