package mb.oauth2authorizationserver.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.api.request.MigrationRequest;
import mb.oauth2authorizationserver.api.request.ScriptGenerationRequest;
import mb.oauth2authorizationserver.api.response.ScriptGenerationResponse;
import mb.oauth2authorizationserver.service.OracleToolsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Controller for Oracle database tools including:
 * - DDL/DCL script generation from PostgreSQL schema
 * - PostgreSQL to Oracle data migration
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/oracle-tools")
@Tag(name = "Oracle Tools", description = "Oracle DDL/DCL script generation and PostgreSQL to Oracle migration")
public class OracleToolsController {

    private static final String CONTENT_DISPOSITION_HEADER = "attachment; filename=\"%s\"";

    private final OracleToolsService oracleToolsService;

    /**
     * Generates Oracle DDL and DCL scripts by analyzing PostgreSQL database schema
     * and returns the formatted SQL file for download.
     *
     * @param request Script generation request containing source database config and options
     * @return Formatted SQL file containing DDL and DCL scripts
     */
    @PostMapping(value = "/generate-scripts", produces = "application/sql")
    @Operation(
            summary = "Generate Oracle DDL/DCL SQL file from PostgreSQL",
            description = """
                    Analyzes a PostgreSQL database schema and generates a formatted Oracle SQL file containing DDL and DCL scripts.
                    
                    **Generated Scripts Include:**
                    - CREATE SEQUENCE statements (single line)
                    - CREATE TABLE statements (multi-line with proper formatting)
                    - CREATE INDEX statements (after each table)
                    - ALTER TABLE for foreign key constraints
                    - Role-based GRANT statements (CREATE ROLE, GRANT to roles, GRANT roles to users)
                    
                    **Role Name Derivation:**
                    If editRoleName/viewRoleName are not provided, they are derived from targetSchema:
                    - mb_oracle_schema → MB_ORACLE_SCHEMA_EDIT_ROLE, MB_ORACLE_SCHEMA_VIEW_ROLE
                    - mb_oracle_schema_env → MB_ORACLE_SCHEMA_EDIT_ROLE, MB_ORACLE_SCHEMA_VIEW_ROLE
                    - NEXTHEACX_WIZAS → HEACX_EDIT_ROLE, HEACX_VIEW_ROLE
                    
                    **Role Assignments Output:**
                    ```sql
                    GRANT MB_ORACLE_SCHEMA_EDIT_ROLE TO myapp_user;
                    GRANT MB_ORACLE_SCHEMA_VIEW_ROLE TO myapp_user, DWHUSER;
                    ```
                    
                    **Returns:** A downloadable .sql file with formatted Oracle scripts.
                    
                    **Example curl request:**
                    ```bash
                    curl -X POST 'http://localhost:8080/api/oracle-tools/generate-scripts' \\
                      -H 'Content-Type: application/json' \\
                      -o oracle_scripts.sql \\
                      -d '{
                        "source": {
                          "jdbcUrl": "jdbc:postgresql://localhost:5432/mydb",
                          "username": "user",
                          "password": "password",
                          "schema": "public"
                        },
                        "targetSchema": "mb_oracle_schema",
                        "editRoleUsers": ["myapp_user"],
                        "viewRoleUsers": ["myapp_user", "DWHUSER"]
                      }'
                    ```
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Script generation configuration",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScriptGenerationRequest.class),
                            examples = @ExampleObject(
                                    name = "Script Generation Request Example",
                                    summary = "Generate all scripts with role-based grants (role names derived from targetSchema)",
                                    value = """
                                            {
                                              "source": {
                                                "jdbcUrl": "jdbc:postgresql://localhost:5432/mydb",
                                                "username": "user",
                                                "password": "password",
                                                "schema": "public"
                                              },
                                              "targetSchema": "mb_oracle_schema",
                                              "editRoleUsers": ["myapp_user"],
                                              "viewRoleUsers": ["myapp_user", "DWHUSER"]
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "SQL file generated successfully",
                            content = @Content(mediaType = "application/sql")
                    ),
                    @ApiResponse(responseCode = "400", description = "Invalid request body"),
                    @ApiResponse(responseCode = "500", description = "Internal server error - could not connect to database or generate scripts")
            }
    )
    public ResponseEntity<byte[]> generateScripts(@Valid @RequestBody ScriptGenerationRequest request) {
        log.info("Script generation requested for PostgreSQL schema: {} -> Oracle schema: {}", request.getSource().getSchema(), request.getTargetSchema());

        ScriptGenerationResponse response = oracleToolsService.generateScripts(request);

        log.info("Script generation completed. Tables processed: {}, Warnings: {}", response.getTableCount(), response.getWarnings().size());

        String filename = String.format("oracle_%s.sql", request.getTargetSchema().toLowerCase());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, String.format(CONTENT_DISPOSITION_HEADER, filename))
                .contentType(MediaType.parseMediaType("application/sql"))
                .body(response.getFullScript().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Triggers the PostgreSQL to Oracle migration process using provided source and destination configuration.
     * The migration runs asynchronously in the background.
     *
     * @param request Migration request containing source and destination database configurations
     * @return Response indicating the migration has been started
     */
    @PostMapping("/migrate")
    @Operation(
            summary = "Start PostgreSQL to Oracle migration",
            description = """
                    Triggers the PostgreSQL to Oracle data migration process.
                    Requires source (PostgreSQL) and destination (Oracle) database configurations in the request body.
                    The migration runs asynchronously in the background.
                    
                    **Example curl request:**
                    ```bash
                    curl -X POST 'http://localhost:8080/api/oracle-tools/migrate' \\
                      -H 'Content-Type: application/json' \\
                      -d '{
                        "source": {
                          "jdbcUrl": "jdbc:postgresql://localhost:5432/mydb",
                          "username": "user",
                          "password": "password",
                          "schema": "myapp_user"
                        },
                        "destination": {
                          "jdbcUrl": "jdbc:oracle:thin:@//localhost:1521/ORCL",
                          "username": "appuser",
                          "password": "password",
                          "schema": "myapp_user"
                        }
                      }'
                    ```
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Migration configuration with source and destination database details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MigrationRequest.class),
                            examples = @ExampleObject(
                                    name = "Migration Request Example",
                                    summary = "PostgreSQL to Oracle migration",
                                    value = """
                                            {
                                              "source": {
                                                "jdbcUrl": "jdbc:postgresql://localhost:5432/mydb",
                                                "username": "user",
                                                "password": "password",
                                                "schema": "myapp_user"
                                              },
                                              "destination": {
                                                "jdbcUrl": "jdbc:oracle:thin:@//localhost:1521/ORCL",
                                                "username": "appuser",
                                                "password": "password",
                                                "schema": "mb_oracle_schema"
                                              }
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Migration started successfully",
                            content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "Migration started successfully. Check logs for progress."))
                    ),
                    @ApiResponse(responseCode = "400", description = "Invalid request body"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<String> startMigration(@Valid @RequestBody MigrationRequest request) {
        log.info("Migration triggered via API. Source: {} (schema: {}), Destination: {} (schema: {})", request.getSource().getJdbcUrl(), request.getSource().getSchema(), request.getDestination().getJdbcUrl(), request.getDestination().getSchema());
        oracleToolsService.migrate(request);
        return ResponseEntity.ok("Migration started successfully. Check logs for progress.");
    }
}
