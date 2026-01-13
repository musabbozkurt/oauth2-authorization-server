package mb.oauth2authorizationserver.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Database connection configuration DTO.
 * Used for configuring source and destination database connections.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Database connection configuration")
public class DatabaseConfig {

    @NotBlank
    @Schema(description = "JDBC URL", example = "jdbc:postgresql://localhost:5432/mydb")
    private String jdbcUrl;

    @NotBlank
    @Schema(description = "Database username")
    private String username;

    @NotBlank
    @Schema(description = "Database password")
    private String password;

    @NotBlank
    @Schema(description = "Schema name", example = "mb_schema")
    private String schema;

    @Schema(description = "JDBC driver class name", example = "org.postgresql.Driver")
    private String driverClassName;
}
