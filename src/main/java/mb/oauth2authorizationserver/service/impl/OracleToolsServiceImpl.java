package mb.oauth2authorizationserver.service.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.api.request.DatabaseConfig;
import mb.oauth2authorizationserver.api.request.MigrationRequest;
import mb.oauth2authorizationserver.api.request.ScriptGenerationRequest;
import mb.oauth2authorizationserver.api.response.ScriptGenerationResponse;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import mb.oauth2authorizationserver.service.OracleToolsService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service implementation for Oracle database tools including:
 * - DDL/DCL script generation from PostgreSQL schema
 * - PostgreSQL to Oracle data migration
 */
@Slf4j
@Service
public class OracleToolsServiceImpl implements OracleToolsService {

    private static final Pattern USING_PATTERN = Pattern.compile("(?i)\\sUSING\\s+\\w+");

    // =============================================
    // CONSTANTS
    // =============================================

    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String DEFAULT_CLAUSE = " DEFAULT ";
    private static final String SECTION_SEPARATOR = "-- =============================================";
    private static final int ORACLE_MAX_IDENTIFIER_LENGTH = 128;

    // Migration constants
    private static final long MIGRATION_LOCK_KEY = 123456789L;
    private static final int BATCH_SIZE = 10000;
    private static final int THREAD_COUNT = 10;
    private static final int PROGRESS_INTERVAL_SECONDS = 30;
    private static final int QUERY_TIMEOUT = 600;

    // Chunked (parallel PK-range) migration constants for large tables
    private static final long CHUNKING_MIN_ROWS = 1_000_000L;
    private static final int DEFAULT_CHUNK_WORKERS = 6;
    private static final int MAX_CHUNK_WORKERS = 24;
    private static final int CHUNKS_PER_WORKER = 8;
    private static final int CHUNK_BATCH_SIZE = 20000;
    private static final int ORACLE_PARALLEL_DEGREE = 8;
    private static final Set<String> NUMERIC_PK_TYPES = Set.of("int2", "int4", "int8");
    private static final Pattern NEXTVAL_SEQUENCE_PATTERN = Pattern.compile("nextval\\('(?:\"?\\w+\"?\\.)?\"?(\\w+)\"?'");

    // Migration state
    private final AtomicBoolean migrationRunning = new AtomicBoolean(false);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private volatile long estimatedTotal = 0;
    private volatile int currentChunkWorkers = DEFAULT_CHUNK_WORKERS;
    private volatile boolean currentRebuildPrimaryKeys = false;
    private HikariDataSource currentOracleDataSource;
    private HikariDataSource currentPostgresDataSource;
    private String currentSourceSchema;
    private String currentTargetSchema;
    private Map<String, String> currentTableSchemaOverrides = Map.of();
    private Set<String> currentTargetSchemas = Set.of();

    // =============================================
    // SCRIPT GENERATION
    // =============================================

    @Override
    public ScriptGenerationResponse generateScripts(ScriptGenerationRequest request) {
        log.info("Starting Oracle script generation from PostgreSQL schema: {}", request.getSource().getSchema());

        HikariDataSource dataSource = null;
        try {
            dataSource = createDataSource(request.getSource(), "ScriptGenPool", 5, 1);
            return processSchemaForScripts(dataSource, request);
        } catch (SQLException e) {
            log.error("Failed to generate scripts: {}", e.getMessage(), e);
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        } finally {
            closeDataSource(dataSource);
        }
    }

    private ScriptGenerationResponse processSchemaForScripts(HikariDataSource dataSource, ScriptGenerationRequest request) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptContext scriptContext = new ScriptContext(request, connection);
            initializeSequenceScripts(scriptContext);
            processTablesForScripts(scriptContext);
            String fullScript = buildDdlScript(scriptContext) + buildDclScript(scriptContext);

            return ScriptGenerationResponse.builder()
                    .tableCount(scriptContext.tablesToProcess.size())
                    .fullScript(fullScript)
                    .warnings(scriptContext.warnings)
                    .build();
        }
    }

    private void processTablesForScripts(ScriptContext scriptContext) {
        log.info("Processing {} tables from schema {}", scriptContext.tablesToProcess.size(), scriptContext.sourceSchema);

        for (String tableName : scriptContext.tablesToProcess) {
            processTableForScripts(scriptContext, tableName);
        }
    }

    private void processTableForScripts(ScriptContext scriptContext, String tableName) {
        try {
            String targetSchema = scriptContext.resolveTargetSchema(tableName);
            boolean hasSequence = scriptContext.tablesWithManagedSequences.contains(tableName.toLowerCase(Locale.ROOT));
            processTableDdl(scriptContext, tableName, targetSchema, hasSequence);
            scriptContext.tableIndexMap.put(tableName, generateIndexScripts(scriptContext.connection, scriptContext.sourceSchema, tableName, targetSchema));
            scriptContext.foreignKeyScripts.addAll(generateForeignKeyScripts(scriptContext.connection, scriptContext.sourceSchema, tableName, targetSchema, scriptContext.tableToTargetSchema, scriptContext.targetSchema, scriptContext.crossSchemaReferenceGrants));
        } catch (Exception e) {
            String warning = String.format("Error processing table %s: %s", tableName, e.getMessage());
            log.warn(warning);
            scriptContext.warnings.add(warning);
        }
    }

    private void initializeSequenceScripts(ScriptContext scriptContext) throws SQLException {
        Set<String> generatedSequenceKeys = new LinkedHashSet<>();
        for (SourceSequenceInfo sourceSequence : getSourceSequences(scriptContext.connection, scriptContext.sourceSchema)) {
            String targetSchema = scriptContext.targetSchema;
            String sequenceName = truncateOracleIdentifier(sourceSequence.sequenceName().toUpperCase(Locale.ROOT));
            String relatedTable = resolveRelatedTableForSequence(sourceSequence.sequenceName(), sourceSequence.ownerTable(), scriptContext.tableToTargetSchema);

            if (relatedTable != null) {
                targetSchema = resolveTargetSchemaForTable(relatedTable, scriptContext.tableToTargetSchema, scriptContext.targetSchema);
            }

            if (sourceSequence.ownerTable() != null) {
                String ownerTable = sourceSequence.ownerTable().toLowerCase(Locale.ROOT);
                sequenceName = truncateOracleIdentifier("SEQ_" + sourceSequence.ownerTable().toUpperCase(Locale.ROOT));
                scriptContext.tablesWithManagedSequences.add(ownerTable);
            }

            String sequenceKey = targetSchema + "." + sequenceName;
            if (generatedSequenceKeys.add(sequenceKey)) {
                scriptContext.sequenceScripts.add(generateSequenceScript(targetSchema, sequenceName));
                scriptContext.schemaSequenceMap.computeIfAbsent(targetSchema, ignored -> new ArrayList<>()).add(sequenceName);
            }
        }
    }

    private void processTableDdl(ScriptContext scriptContext, String tableName, String targetSchema, boolean hasSequence) throws SQLException {
        String tableScript = generateTableScript(scriptContext.connection, scriptContext.sourceSchema, tableName, targetSchema, hasSequence, scriptContext.warnings);
        if (!tableScript.isEmpty()) {
            scriptContext.tableScripts.add(tableScript);
        }
    }

    private String buildDdlScript(ScriptContext scriptContext) {
        StringBuilder ddl = new StringBuilder();
        appendSequencesSection(ddl, scriptContext.sequenceScripts);
        appendTablesSection(ddl, scriptContext);
        appendForeignKeysSection(ddl, scriptContext.foreignKeyScripts);
        return ddl.toString();
    }

    private void appendSequencesSection(StringBuilder ddl, List<String> sequenceScripts) {
        if (sequenceScripts.isEmpty()) return;

        ddl.append(SECTION_SEPARATOR).append(LINE_SEPARATOR);
        ddl.append("-- SEQUENCES").append(LINE_SEPARATOR);
        ddl.append(SECTION_SEPARATOR).append(LINE_SEPARATOR);
        sequenceScripts.forEach(script -> ddl.append(script).append(LINE_SEPARATOR));
    }

    private void appendTablesSection(StringBuilder ddl, ScriptContext scriptContext) {
        if (scriptContext.tableScripts.isEmpty()) return;

        ddl.append(LINE_SEPARATOR).append(SECTION_SEPARATOR).append(LINE_SEPARATOR);
        ddl.append("-- TABLES").append(LINE_SEPARATOR);
        ddl.append(SECTION_SEPARATOR).append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        int tableIndex = 0;
        for (String tableName : scriptContext.tablesToProcess) {
            if (tableIndex < scriptContext.tableScripts.size()) {
                appendTableWithIndexes(ddl, tableName, scriptContext.tableScripts.get(tableIndex), scriptContext.tableIndexMap.get(tableName));
                tableIndex++;
            }
        }
    }

    private void appendTableWithIndexes(StringBuilder ddl, String tableName, String tableScript, List<String> indexes) {
        ddl.append("-- ").append(tableName).append(" table").append(LINE_SEPARATOR);
        ddl.append(tableScript).append(LINE_SEPARATOR);

        if (indexes != null && !indexes.isEmpty()) {
            ddl.append(LINE_SEPARATOR);
            indexes.forEach(idx -> ddl.append(idx).append(LINE_SEPARATOR));
        }
        ddl.append(LINE_SEPARATOR);
    }

    private void appendForeignKeysSection(StringBuilder ddl, List<String> foreignKeyScripts) {
        if (foreignKeyScripts.isEmpty()) return;

        ddl.append(SECTION_SEPARATOR).append(LINE_SEPARATOR);
        ddl.append("-- FOREIGN KEYS").append(LINE_SEPARATOR);
        ddl.append(SECTION_SEPARATOR).append(LINE_SEPARATOR);
        foreignKeyScripts.forEach(script -> ddl.append(script).append(LINE_SEPARATOR));
    }

    private String buildDclScript(ScriptContext scriptContext) {
        StringBuilder dcl = new StringBuilder();
        Map<String, Set<String>> editRoleUsersBySchema = normalizeRoleUsersBySchema(scriptContext.scriptGenerationRequest.getEditRoleUsersBySchema());
        Map<String, Set<String>> viewRoleUsersBySchema = normalizeRoleUsersBySchema(scriptContext.scriptGenerationRequest.getViewRoleUsersBySchema());

        appendDclHeader(dcl);
        appendCrossSchemaReferencesGrants(dcl, scriptContext.crossSchemaReferenceGrants);

        Set<String> schemasForDcl = new LinkedHashSet<>(scriptContext.schemaTableMap.keySet());
        schemasForDcl.addAll(scriptContext.schemaSequenceMap.keySet());
        boolean useSchemaDerivedRoles = scriptContext.mapBasedMode;
        Set<String> createdRoles = new HashSet<>();
        for (String schema : schemasForDcl) {
            RolePair rolePair = resolveRolePair(scriptContext, schema, useSchemaDerivedRoles);
            Set<String> schemaEditRoleUsers = resolveRoleUsersForSchemaWithoutFallback(schema, editRoleUsersBySchema);
            Set<String> schemaViewRoleUsers = resolveRoleUsersForSchemaWithoutFallback(schema, viewRoleUsersBySchema);
            if (createdRoles.add(rolePair.editRole())) {
                dcl.append("CREATE ROLE ").append(rolePair.editRole()).append(";").append(LINE_SEPARATOR);
            }
            if (createdRoles.add(rolePair.viewRole())) {
                dcl.append("CREATE ROLE ").append(rolePair.viewRole()).append(";").append(LINE_SEPARATOR);
            }
            appendSequenceGrants(dcl, scriptContext.schemaSequenceMap.getOrDefault(schema, List.of()), schema, rolePair.editRole());
            appendTableGrants(dcl, scriptContext.schemaTableMap.getOrDefault(schema, List.of()), schema, rolePair.editRole(), rolePair.viewRole());
            appendRoleAssignments(dcl, rolePair.editRole(), rolePair.viewRole(), schemaEditRoleUsers, schemaViewRoleUsers);
            dcl.append(LINE_SEPARATOR);
        }

        return dcl.toString();
    }

    private void appendCrossSchemaReferencesGrants(StringBuilder dcl, Set<String> crossSchemaReferenceGrants) {
        if (crossSchemaReferenceGrants.isEmpty()) {
            return;
        }

        dcl.append(SECTION_SEPARATOR).append(LINE_SEPARATOR);
        dcl.append("-- CROSS-SCHEMA REFERENCES GRANTS").append(LINE_SEPARATOR);
        dcl.append(SECTION_SEPARATOR).append(LINE_SEPARATOR);
        dcl.append("-- Child schemas need REFERENCES on parent table before cross-schema FKs.").append(LINE_SEPARATOR);
        dcl.append("-- Run as parent schema owner or DBA.").append(LINE_SEPARATOR);
        crossSchemaReferenceGrants.forEach(grant -> dcl.append(grant).append(LINE_SEPARATOR));
        dcl.append(LINE_SEPARATOR);
    }

    private RolePair resolveRolePair(ScriptContext scriptContext, String schema, boolean forceDerivedForMappedSchemas) {
        if (forceDerivedForMappedSchemas) {
            String roleBaseName = deriveRoleBaseNameKeepSuffix(schema);
            return new RolePair("%s_EDIT_ROLE".formatted(roleBaseName), "%s_VIEW_ROLE".formatted(roleBaseName));
        }

        String roleBaseName = deriveRoleBaseName(schema);
        String editRole = scriptContext.scriptGenerationRequest.getEditRoleName() != null ? scriptContext.scriptGenerationRequest.getEditRoleName() : "%s_EDIT_ROLE".formatted(roleBaseName);
        String viewRole = scriptContext.scriptGenerationRequest.getViewRoleName() != null ? scriptContext.scriptGenerationRequest.getViewRoleName() : "%s_VIEW_ROLE".formatted(roleBaseName);
        return new RolePair(editRole, viewRole);
    }

    private String deriveRoleBaseNameKeepSuffix(String schema) {
        if (schema == null || schema.isBlank()) {
            return "DEFAULT";
        }

        return schema.toUpperCase(Locale.ROOT).trim();
    }

    private Map<String, Set<String>> normalizeRoleUsersBySchema(Map<String, Set<String>> roleUsersBySchema) {
        if (roleUsersBySchema == null || roleUsersBySchema.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : roleUsersBySchema.entrySet()) {
            String schema = entry.getKey();
            Set<String> users = entry.getValue();
            if (schema == null || schema.isBlank() || CollectionUtils.isEmpty(users)) {
                continue;
            }
            normalized.put(schema.toUpperCase(Locale.ROOT), users);
        }
        return normalized;
    }

    private Set<String> resolveRoleUsersForSchemaWithoutFallback(String schema, Map<String, Set<String>> roleUsersBySchema) {
        String normalizedSchema = schema.toUpperCase(Locale.ROOT);
        Set<String> schemaUsers = roleUsersBySchema.get(normalizedSchema);
        if (CollectionUtils.isNotEmpty(schemaUsers)) {
            return schemaUsers;
        }

        int underscoreIndex = normalizedSchema.indexOf('_');
        if (underscoreIndex > 0) {
            String baseSchema = normalizedSchema.substring(0, underscoreIndex);
            Set<String> baseSchemaUsers = roleUsersBySchema.get(baseSchema);
            if (CollectionUtils.isNotEmpty(baseSchemaUsers)) {
                return baseSchemaUsers;
            }
        }

        throw new IllegalArgumentException("Missing role user mapping for schema '" + schema + "'.");
    }

    /**
     * Derives the role base name from the target schema.
     * Examples:
     * - mb_oracle_schema -> MB_ORACLE_SCHEMA
     * - mb_oracle_schema_env -> MB_ORACLE_SCHEMA
     * - core_zone_app -> CORE
     */
    private String deriveRoleBaseName(String targetSchema) {
        if (targetSchema == null || targetSchema.isEmpty()) {
            return "DEFAULT";
        }

        String baseName = targetSchema.toUpperCase(Locale.ROOT);

        // Remove suffix after underscore (e.g., _env, _WIZAS)
        int underscoreIndex = baseName.indexOf('_');
        if (underscoreIndex > 0) {
            baseName = baseName.substring(0, underscoreIndex);
        }

        return baseName;
    }

    private void appendDclHeader(StringBuilder dcl) {
        dcl.append(LINE_SEPARATOR).append(SECTION_SEPARATOR).append(LINE_SEPARATOR);
        dcl.append("-- ROLES AND GRANTS").append(LINE_SEPARATOR);
        dcl.append(SECTION_SEPARATOR).append(LINE_SEPARATOR).append(LINE_SEPARATOR);
    }


    private void appendSequenceGrants(StringBuilder dcl, List<String> sequences, String schema, String editRole) {
        if (sequences.isEmpty()) return;

        dcl.append("-- Sequence Grants").append(LINE_SEPARATOR);
        for (String seqName : sequences) {
            dcl.append("GRANT SELECT ON ").append(schema).append(".").append(seqName).append(" TO ").append(editRole).append(";").append(LINE_SEPARATOR);
        }
        dcl.append(LINE_SEPARATOR);
    }

    private void appendTableGrants(StringBuilder dcl, List<String> tables, String schema, String editRole, String viewRole) {
        dcl.append("-- Table Grants - Edit Role").append(LINE_SEPARATOR);
        for (String tableName : tables) {
            dcl.append("GRANT SELECT, INSERT, UPDATE, DELETE ON ").append(schema).append(".").append(tableName.toUpperCase()).append(" TO ").append(editRole).append(";").append(LINE_SEPARATOR);
        }
        dcl.append(LINE_SEPARATOR);

        dcl.append("-- Table Grants - View Role").append(LINE_SEPARATOR);
        for (String tableName : tables) {
            dcl.append("GRANT SELECT ON ").append(schema).append(".").append(tableName.toUpperCase()).append(" TO ").append(viewRole).append(";").append(LINE_SEPARATOR);
        }
        dcl.append(LINE_SEPARATOR);
    }

    private void appendRoleAssignments(StringBuilder dcl, String editRole, String viewRole, Set<String> editRoleUsers, Set<String> viewRoleUsers) {
        dcl.append("-- Role Assignments").append(LINE_SEPARATOR);
        dcl.append("GRANT ").append(editRole).append(" TO ").append(String.join(", ", editRoleUsers)).append(";").append(LINE_SEPARATOR);
        dcl.append("GRANT ").append(viewRole).append(" TO ").append(String.join(", ", viewRoleUsers)).append(";").append(LINE_SEPARATOR);
    }

    @Async
    @Override
    public void migrate(MigrationRequest request) {
        // In-JVM guard: a second call must not overwrite the shared datasources/settings of a running migration
        // (the advisory lock alone is checked only after those fields are set).
        if (!migrationRunning.compareAndSet(false, true)) {
            log.warn("Migration is already running in this instance. Skipping this request.");
            return;
        }
        try {
            totalProcessed.set(0);
            estimatedTotal = 0;

            DatabaseConfig source = request.getSource();
            DatabaseConfig destination = request.getDestination();
            Integer chunkWorkers = request.getChunkWorkers();
            currentChunkWorkers = chunkWorkers == null || chunkWorkers < 1 ? DEFAULT_CHUNK_WORKERS : Math.min(chunkWorkers, MAX_CHUNK_WORKERS);
            currentRebuildPrimaryKeys = request.getRebuildPrimaryKeys() == null ? MigrationRequest.DEFAULT_REBUILD_PRIMARY_KEYS : request.getRebuildPrimaryKeys();
            int poolSize = Math.max(30, currentChunkWorkers + THREAD_COUNT + 5);
            currentPostgresDataSource = createDataSource(withBinaryTransfer(source), "PostgresPool", poolSize, 5);
            currentOracleDataSource = createDataSource(destination, "OraclePool", poolSize, 5);
            currentSourceSchema = source.getSchema();
            currentTableSchemaOverrides = buildTableSchemaOverrides(request.getTableSchemaMap(), "migration");
            currentTargetSchema = resolveDefaultSchema(currentTableSchemaOverrides, destination.getSchema().toUpperCase(Locale.ROOT));
            currentTargetSchemas = resolveAllTargetSchemas(currentTargetSchema, currentTableSchemaOverrides);

            log.info("Created datasources - Source: {} (schema: {}), Destination: {} (schema: {})", source.getJdbcUrl(), currentSourceSchema, destination.getJdbcUrl(), currentTargetSchema);

            executeMigration();
        } finally {
            closeDataSource(currentPostgresDataSource);
            closeDataSource(currentOracleDataSource);
            migrationRunning.set(false);
        }
    }

    private String targetTableName(TableMapping tableMapping) {
        return tableMapping.targetTable().substring(tableMapping.targetTable().indexOf('.') + 1);
    }

    // =============================================
    // DATA MIGRATION
    // =============================================

    private void executeMigration() {
        Connection lockConnection = null;
        try {
            lockConnection = currentPostgresDataSource.getConnection();
            lockConnection.setAutoCommit(false);

            if (!acquireLock(lockConnection)) {
                log.warn("Migration is already running. Skipping this execution.");
                return;
            }

            log.info("Acquired migration lock. Starting PostgreSQL to Oracle migration.");
            performMigration();
        } catch (SQLException e) {
            log.error("Failed to acquire migration lock. Exception: {}", ExceptionUtils.getStackTrace(e));
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        } finally {
            releaseLock(lockConnection);
        }
    }

    private boolean acquireLock(Connection connection) throws SQLException {
        String lockQuery = "SELECT pg_try_advisory_lock(?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(lockQuery)) {
            preparedStatement.setLong(1, MIGRATION_LOCK_KEY);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private void releaseLock(Connection connection) {
        if (connection != null) {
            try {
                String unlockQuery = "SELECT pg_advisory_unlock(?)";
                try (PreparedStatement preparedStatement = connection.prepareStatement(unlockQuery)) {
                    preparedStatement.setLong(1, MIGRATION_LOCK_KEY);
                    preparedStatement.executeQuery();
                }
                connection.close();
                log.info("Released migration lock");
            } catch (SQLException e) {
                log.error("Failed to release lock. Exception: {}", ExceptionUtils.getStackTrace(e));
            }
        }
    }

    private void performMigration() {
        Instant start = Instant.now();
        log.info("Starting PostgreSQL to Oracle migration with {} threads, batch size: {}", THREAD_COUNT, BATCH_SIZE);

        List<TableMapping> tableMappings = getTableMappingsForMigration();
        estimatedTotal = getEstimatedTotal(tableMappings);

        disableAllForeignKeys();

        CountDownLatch latch = new CountDownLatch(tableMappings.size());

        try (ExecutorService chunkExecutor = Executors.newFixedThreadPool(currentChunkWorkers);
             ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
             ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor()) {

            for (TableMapping mapping : tableMappings) {
                executor.submit(() -> {
                    try {
                        migrateTable(mapping, chunkExecutor);
                    } catch (Exception e) {
                        log.error("Failed to migrate table {}: {}", mapping.sourceTable(), e.getMessage(), e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            monitor.scheduleAtFixedRate(() -> logProgress(start), PROGRESS_INTERVAL_SECONDS, PROGRESS_INTERVAL_SECONDS, TimeUnit.SECONDS);

            latch.await();
            monitor.shutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Migration interrupted. Exception: {}", ExceptionUtils.getStackTrace(e));
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        } finally {
            updateAllSequences();
            enableAllForeignKeys();
        }

        logFinalStatistics(start);
    }

    private void updateAllSequences() {
        for (String schema : currentTargetSchemas) {
            updateSequencesForSchema(schema);
        }
    }

    private void updateSequencesForSchema(String targetSchema) {
        log.info("Updating all sequences to latest record values in schema {}.", targetSchema);

        String findSequencesQuery = """
                SELECT sequence_name
                FROM all_sequences
                WHERE sequence_owner = ?
                ORDER BY sequence_name
                """;

        try (Connection connection = currentOracleDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(findSequencesQuery)) {

            preparedStatement.setString(1, targetSchema);
            int updatedCount = 0;
            int skippedCount = 0;

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String sequenceName = resultSet.getString("sequence_name");
                    String tableName = deriveTableNameFromSequence(sequenceName);

                    if (tableName != null) {
                        boolean updated = updateSequenceToMaxValue(targetSchema, sequenceName, tableName);
                        if (updated) {
                            updatedCount++;
                        } else {
                            skippedCount++;
                        }
                    } else {
                        log.debug("Could not derive table name from sequence: {}", sequenceName);
                        skippedCount++;
                    }
                }
            }

            log.info("Completed updating sequences in schema {} - Updated: {}, Skipped: {}", targetSchema, updatedCount, skippedCount);
        } catch (SQLException e) {
            log.error("Failed to update sequences: {}", e.getMessage(), e);
        }
    }

    private String deriveTableNameFromSequence(String sequenceName) {
        if (sequenceName == null) return null;
        if (sequenceName.startsWith("SEQ_")) {
            return sequenceName.substring(4);
        }
        return null;
    }

    private boolean updateSequenceToMaxValue(String targetSchema, String sequenceName, String tableName) {
        String fullTableName = targetSchema + "." + tableName;
        String fullSequenceName = targetSchema + "." + sequenceName;

        try (Connection connection = currentOracleDataSource.getConnection();
             Statement stmt = connection.createStatement()) {

            String checkTableQuery = "SELECT COUNT(*) FROM all_tables WHERE owner = '%s' AND table_name = '%s'".formatted(targetSchema, tableName);
            try (ResultSet resultSet = stmt.executeQuery(checkTableQuery)) {
                if (resultSet.next() && resultSet.getInt(1) == 0) {
                    log.debug("Table {} does not exist, skipping sequence {}", fullTableName, fullSequenceName);
                    return false;
                }
            }

            String checkColumnQuery = "SELECT COUNT(*) FROM all_tab_columns WHERE owner = '%s' AND table_name = '%s' AND column_name = '%s'".formatted(targetSchema, tableName, "ID");
            try (ResultSet resultSet = stmt.executeQuery(checkColumnQuery)) {
                if (resultSet.next() && resultSet.getInt(1) == 0) {
                    log.debug("Column {} does not exist in table {}, skipping sequence {}", "ID", fullTableName, fullSequenceName);
                    return false;
                }
            }

            String maxQuery = "SELECT NVL(MAX(%s), 0) FROM %s".formatted("ID", fullTableName);
            long maxValue = 0;

            try (ResultSet resultSet = stmt.executeQuery(maxQuery)) {
                if (resultSet.next()) {
                    maxValue = resultSet.getLong(1);
                }
            }

            if (maxValue > 0) {
                String getCurrentSeqQuery = "SELECT %s.NEXTVAL FROM DUAL".formatted(fullSequenceName);
                long currentSeqValue = 0;

                try (ResultSet resultSet = stmt.executeQuery(getCurrentSeqQuery)) {
                    if (resultSet.next()) {
                        currentSeqValue = resultSet.getLong(1);
                    }
                }

                long increment = maxValue - currentSeqValue;

                if (increment > 0) {
                    stmt.execute("ALTER SEQUENCE %s INCREMENT BY %d".formatted(fullSequenceName, increment));
                    try (ResultSet resultSet = stmt.executeQuery("SELECT %s.NEXTVAL FROM DUAL".formatted(fullSequenceName))) {
                        // Just execute to advance the sequence
                    }
                    stmt.execute("ALTER SEQUENCE %s INCREMENT BY 1".formatted(fullSequenceName));
                    log.info("Updated sequence {} from {} to {}", fullSequenceName, currentSeqValue, maxValue + 1);
                    return true;
                } else {
                    log.debug("Sequence {} already at or above max value (seq={}, max={})", fullSequenceName, currentSeqValue, maxValue);
                    return false;
                }
            } else {
                log.debug("Table {} is empty, skipping sequence update for {}", fullTableName, fullSequenceName);
                return false;
            }
        } catch (SQLException e) {
            log.warn("Could not update sequence {}: {}", fullSequenceName, e.getMessage());
            return false;
        }
    }

    private void disableAllForeignKeys() {
        for (String schema : currentTargetSchemas) {
            disableForeignKeysForSchema(schema);
        }
    }

    private void disableForeignKeysForSchema(String targetSchema) {
        log.info("Disabling all foreign key constraints in schema {}.", targetSchema);
        String sql = """
                BEGIN
                    FOR c IN (SELECT constraint_name, table_name
                              FROM all_constraints
                              WHERE owner = '%s'
                                AND constraint_type = 'R'
                                AND status = 'ENABLED')
                        LOOP
                            BEGIN
                                EXECUTE IMMEDIATE 'ALTER TABLE %s.' || c.table_name || ' DISABLE CONSTRAINT ' || c.constraint_name;
                            EXCEPTION
                                WHEN OTHERS THEN NULL;
                            END;
                        END LOOP;
                END;
                """.formatted(targetSchema, targetSchema);

        executeOraclePLSQL(sql, "disable foreign keys");
    }

    private void enableAllForeignKeys() {
        for (String schema : currentTargetSchemas) {
            enableForeignKeysForSchema(schema);
        }
    }

    private void enableForeignKeysForSchema(String targetSchema) {
        log.info("Enabling all foreign key constraints in schema {}.", targetSchema);
        String sql = """
                BEGIN
                    FOR c IN (SELECT constraint_name, table_name
                              FROM all_constraints
                              WHERE owner = '%s'
                                AND constraint_type = 'R'
                                AND status = 'DISABLED')
                        LOOP
                            BEGIN
                                EXECUTE IMMEDIATE 'ALTER TABLE %s.' || c.table_name || ' ENABLE CONSTRAINT ' || c.constraint_name;
                            EXCEPTION
                                WHEN OTHERS THEN NULL;
                            END;
                        END LOOP;
                END;
                """.formatted(targetSchema, targetSchema);

        executeOraclePLSQL(sql, "enable foreign keys");
    }

    private void executeOraclePLSQL(String sql, String operation) {
        try (Connection connection = currentOracleDataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("Successfully executed: {}", operation);
        } catch (SQLException e) {
            log.error("Failed to {}: {}", operation, e.getMessage(), e);
        }
    }

    private List<TableMapping> getTableMappingsForMigration() {
        List<TableMapping> mappings = new ArrayList<>();
        boolean mapBasedMode = !currentTableSchemaOverrides.isEmpty();

        String query = """
                SELECT schemaname, tablename
                FROM pg_catalog.pg_tables
                WHERE schemaname = ?
                ORDER BY tablename
                """;

        try (Connection connection = currentPostgresDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {

            preparedStatement.setString(1, currentSourceSchema);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String schema = resultSet.getString("schemaname");
                    String tableName = resultSet.getString("tablename");

                    if (mapBasedMode && !currentTableSchemaOverrides.containsKey(tableName.toLowerCase(Locale.ROOT))) {
                        log.warn("Table '{}' is not present in tableSchemaMap. Skipping table in map-based migration mode.", tableName);
                        continue;
                    }

                    String sourceTable = "%s.%s".formatted(schema, tableName);
                    String resolvedTargetSchema = resolveTargetSchemaForTable(tableName, currentTableSchemaOverrides, currentTargetSchema);
                    String targetTable = "%s.%s".formatted(resolvedTargetSchema, tableName.toUpperCase(Locale.ROOT));

                    mappings.add(new TableMapping(sourceTable, targetTable, resolvedTargetSchema));
                    log.debug("Added table mapping: {} -> {}", sourceTable, targetTable);
                }
            }

            log.info("Discovered {} tables for migration from schema '{}'", mappings.size(), currentSourceSchema);
        } catch (SQLException e) {
            log.error("Failed to discover tables from PostgreSQL: {}", e.getMessage(), e);
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        }

        return mappings;
    }

    private long getEstimatedTotal(List<TableMapping> tableMappings) {
        long total = 0;
        for (TableMapping mapping : tableMappings) {
            // Planner estimate is instant; exact COUNT(*) on billion-row tables can take very long. Only used for progress/ETA logs.
            String estimateQuery = "SELECT reltuples::bigint FROM pg_class WHERE oid = '%s'::regclass".formatted(mapping.sourceTable());
            String countQuery = "SELECT COUNT(*) FROM %s".formatted(mapping.sourceTable());
            try (Connection connection = currentPostgresDataSource.getConnection()) {
                long estimate = queryLong(connection, estimateQuery);
                total += estimate > 0 ? estimate : queryLong(connection, countQuery);
            } catch (SQLException e) {
                log.warn("Could not get count for table {}: {}", mapping.sourceTable(), e.getMessage());
            }
        }
        log.info("Estimated total records to migrate: {}", total);
        return total;
    }

    private long queryLong(Connection connection, String sql) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private boolean hasAtLeastChunkingMinRows(Connection connection, String table) throws SQLException {
        return queryLong(connection, "SELECT COUNT(*) FROM (SELECT 1 FROM %s LIMIT %d) limited".formatted(table, CHUNKING_MIN_ROWS)) >= CHUNKING_MIN_ROWS;
    }

    /**
     * Large tables with a single numeric primary key are copied in parallel PK-range chunks.
     * All other tables use the original single-stream copy (unchanged behaviour).
     */
    private void migrateTable(TableMapping tableMapping, ExecutorService chunkExecutor) throws SQLException {
        if (tableMapping.selectQuery() == null) {
            ChunkPlan plan = planChunks(tableMapping);
            if (plan != null) {
                migrateTableInChunks(tableMapping, plan, chunkExecutor);
                return;
            }
        }
        migrateTable(tableMapping);
    }

    private ChunkPlan planChunks(TableMapping tableMapping) {
        try (Connection connection = currentPostgresDataSource.getConnection()) {
            long estimatedRows = queryLong(connection, "SELECT reltuples::bigint FROM pg_class WHERE oid = '%s'::regclass".formatted(tableMapping.sourceTable()));
            // reltuples is -1/0 or stale for tables that were never analyzed (e.g. freshly restored); a bounded count
            // (reads at most CHUNKING_MIN_ROWS rows) avoids copying such a large table single-stream by mistake.
            if (estimatedRows < CHUNKING_MIN_ROWS && !hasAtLeastChunkingMinRows(connection, tableMapping.sourceTable())) {
                return null;
            }

            String pkQuery = """
                    SELECT a.attname, t.typname
                    FROM pg_index i
                             JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                             JOIN pg_type t ON t.oid = a.atttypid
                    WHERE i.indrelid = '%s'::regclass AND i.indisprimary
                    """.formatted(tableMapping.sourceTable());
            List<String> pkColumns = new ArrayList<>();
            String pkType = null;
            try (PreparedStatement ps = connection.prepareStatement(pkQuery);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pkColumns.add(rs.getString(1));
                    pkType = rs.getString(2);
                }
            }
            if (pkColumns.size() != 1 || !NUMERIC_PK_TYPES.contains(pkType)) {
                log.info("Table {} is large but has no single numeric primary key. Using single-stream copy.", tableMapping.sourceTable());
                return null;
            }

            String pkColumn = pkColumns.getFirst();
            String minMaxQuery = "SELECT MIN(\"%s\"), MAX(\"%s\") FROM %s".formatted(pkColumn, pkColumn, tableMapping.sourceTable());
            try (PreparedStatement ps = connection.prepareStatement(minMaxQuery);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long min = rs.getLong(1);
                if (rs.wasNull()) {
                    return null;
                }
                long max = rs.getLong(2);
                long span = max - min;
                if (span < 0 || span == Long.MAX_VALUE) {
                    return null;
                }
                return new ChunkPlan(pkColumn, min, max);
            }
        } catch (SQLException e) {
            log.warn("Could not plan chunks for {}: {}. Using single-stream copy.", tableMapping.sourceTable(), e.getMessage());
            return null;
        }
    }

    private void migrateTableInChunks(TableMapping tableMapping, ChunkPlan plan, ExecutorService chunkExecutor) throws SQLException {
        Instant tableStart = Instant.now();
        assertTargetTableEmpty(tableMapping.targetTable());

        List<ChunkRange> ranges = buildChunkRanges(plan);
        log.info("Starting chunked migration of table: {} -> {} (pk: {}, range: {}..{}, workers: {}, chunks: {})", tableMapping.sourceTable(), tableMapping.targetTable(), plan.pkColumn(), plan.min(), plan.max(), currentChunkWorkers, ranges.size());

        // Optional: drop the PK index so inserts don't maintain it row by row; it is always rebuilt afterwards (also on failure).
        PrimaryKeyIndex droppedPrimaryKey = currentRebuildPrimaryKeys ? dropPrimaryKeyIndex(tableMapping) : null;
        long tableProcessed;
        try {
            AtomicBoolean failed = new AtomicBoolean(false);
            List<Future<Long>> futures = submitChunks(tableMapping, plan.pkColumn(), ranges, chunkExecutor, failed);
            tableProcessed = collectChunkResults(tableMapping, futures, failed);
        } finally {
            if (droppedPrimaryKey != null) {
                rebuildPrimaryKeyIndex(tableMapping, droppedPrimaryKey);
            }
        }

        Duration duration = Duration.between(tableStart, Instant.now());
        log.info("Completed chunked migration of table {} -> {}. Records: {}, Duration: {}s", tableMapping.sourceTable(), tableMapping.targetTable(), tableProcessed, duration.getSeconds());
    }

    /**
     * Splits [min, max] into workers * CHUNKS_PER_WORKER ranges. The first range has no lower bound and the last has no
     * upper bound, so every row is covered exactly once.
     */
    private List<ChunkRange> buildChunkRanges(ChunkPlan plan) {
        long chunkCount = (long) currentChunkWorkers * CHUNKS_PER_WORKER;
        long chunkSize = (plan.max() - plan.min()) / chunkCount + 1;
        List<ChunkRange> ranges = new ArrayList<>();
        long lower = plan.min();
        boolean last = false;
        while (!last) {
            long next = lower + chunkSize;
            last = next > plan.max() || next < lower;
            ranges.add(new ChunkRange(lower == plan.min() ? null : lower, last ? null : next));
            lower = next;
        }
        return ranges;
    }

    private List<Future<Long>> submitChunks(TableMapping tableMapping, String pkColumn, List<ChunkRange> ranges, ExecutorService chunkExecutor, AtomicBoolean failed) {
        List<Future<Long>> futures = new ArrayList<>();
        for (ChunkRange range : ranges) {
            futures.add(chunkExecutor.submit(() -> migrateChunk(tableMapping, pkColumn, range.lower(), range.upper(), failed)));
        }
        return futures;
    }

    private long collectChunkResults(TableMapping tableMapping, List<Future<Long>> futures, AtomicBoolean failed) throws SQLException {
        long processed = 0;
        Throwable firstError = null;
        for (Future<Long> future : futures) {
            try {
                processed += future.get();
            } catch (ExecutionException e) {
                failed.set(true);
                firstError = firstError == null ? e.getCause() : firstError;
            } catch (InterruptedException e) {
                failed.set(true);
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while migrating " + tableMapping.sourceTable(), e);
            }
        }

        if (firstError != null) {
            throw new SQLException("Chunked migration failed for %s. Target table %s may contain partial data and must be truncated before re-running. Cause: %s".formatted(tableMapping.sourceTable(), tableMapping.targetTable(), firstError.getMessage()), firstError);
        }
        return processed;
    }

    private void assertTargetTableEmpty(String targetTable) throws SQLException {
        try (Connection connection = currentOracleDataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM %s WHERE ROWNUM = 1".formatted(targetTable));
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                throw new SQLException("Target table %s is not empty. Truncate it before chunked migration to avoid duplicates.".formatted(targetTable));
            }
        }
    }

    /**
     * Disables the enabled primary key of the target table and drops its index. Returns null (and leaves the table untouched)
     * when the table has no enabled primary key or the change is not possible, so the load simply runs with the index.
     */
    private PrimaryKeyIndex dropPrimaryKeyIndex(TableMapping tableMapping) {
        String owner = tableMapping.targetSchema();
        String tableName = targetTableName(tableMapping);
        String pkQuery = """
                SELECT c.constraint_name, c.index_name,
                       LISTAGG('"' || cc.column_name || '"', ', ') WITHIN GROUP (ORDER BY cc.position)
                FROM all_constraints c
                         JOIN all_cons_columns cc
                              ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.constraint_name = c.constraint_name
                WHERE c.owner = ? AND c.table_name = ? AND c.constraint_type = 'P' AND c.status = 'ENABLED'
                GROUP BY c.constraint_name, c.index_name
                """;
        try (Connection connection = currentOracleDataSource.getConnection()) {
            PrimaryKeyIndex primaryKey = null;
            try (PreparedStatement ps = connection.prepareStatement(pkQuery)) {
                ps.setString(1, owner);
                ps.setString(2, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String constraintName = rs.getString(1);
                        String indexName = rs.getString(2) == null ? constraintName : rs.getString(2);
                        primaryKey = new PrimaryKeyIndex(owner, constraintName, indexName, rs.getString(3));
                    }
                }
            }
            if (primaryKey == null) {
                log.info("Table {} has no enabled primary key, nothing to drop before load.", tableMapping.targetTable());
                return null;
            }
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE %s DISABLE CONSTRAINT \"%s\" DROP INDEX".formatted(tableMapping.targetTable(), primaryKey.constraintName()));
            }
            log.info("Disabled primary key {} and dropped its index on {} for faster load. It will be rebuilt after the load.", primaryKey.constraintName(), tableMapping.targetTable());
            return primaryKey;
        } catch (SQLException e) {
            log.warn("Could not drop primary key index on {}: {}. Loading with the index in place.", tableMapping.targetTable(), e.getMessage());
            return null;
        }
    }

    /**
     * Rebuilds the primary key index in parallel and re-enables the constraint. Never throws: on failure the exact SQL to
     * run manually is logged so the table is never silently left without its primary key.
     */
    private void rebuildPrimaryKeyIndex(TableMapping tableMapping, PrimaryKeyIndex primaryKey) {
        String indexName = "\"%s\".\"%s\"".formatted(primaryKey.owner(), primaryKey.indexName());
        String createIndex = "CREATE UNIQUE INDEX %s ON %s (%s) PARALLEL %d NOLOGGING".formatted(indexName, tableMapping.targetTable(), primaryKey.columns(), ORACLE_PARALLEL_DEGREE);
        String enableConstraint = "ALTER TABLE %s ENABLE CONSTRAINT \"%s\"".formatted(tableMapping.targetTable(), primaryKey.constraintName());
        Instant rebuildStart = Instant.now();
        log.info("Rebuilding primary key {} on {} (parallel {}).", primaryKey.constraintName(), tableMapping.targetTable(), ORACLE_PARALLEL_DEGREE);

        try (Connection connection = currentOracleDataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            if (!oracleIndexExists(connection, primaryKey)) {
                stmt.execute(createIndex);
            }
            stmt.execute(enableConstraint);
            stmt.execute("ALTER INDEX %s NOPARALLEL".formatted(indexName));
            stmt.execute("ALTER INDEX %s LOGGING".formatted(indexName));
            log.info("Rebuilt primary key {} on {} in {}s.", primaryKey.constraintName(), tableMapping.targetTable(), Duration.between(rebuildStart, Instant.now()).getSeconds());
        } catch (SQLException e) {
            log.error("Failed to rebuild primary key {} on {}: {}. Run manually: {}; {}; ALTER INDEX {} NOPARALLEL; ALTER INDEX {} LOGGING;", primaryKey.constraintName(), tableMapping.targetTable(), e.getMessage(), createIndex, enableConstraint, indexName, indexName, e);
        }
    }

    private boolean oracleIndexExists(Connection connection, PrimaryKeyIndex primaryKey) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM all_indexes WHERE owner = ? AND index_name = ?")) {
            ps.setString(1, primaryKey.owner());
            ps.setString(2, primaryKey.indexName());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private long migrateChunk(TableMapping tableMapping, String pkColumn, Long lower, Long upper, AtomicBoolean failed) throws SQLException {
        if (failed.get()) {
            return 0;
        }

        String quotedPk = "\"" + pkColumn + "\"";
        StringBuilder where = new StringBuilder();
        if (lower != null) {
            where.append(quotedPk).append(" >= ?");
        }
        if (upper != null) {
            if (!where.isEmpty()) {
                where.append(" AND ");
            }
            where.append(quotedPk).append(" < ?");
        }
        String selectQuery = "SELECT * FROM %s%s".formatted(tableMapping.sourceTable(), where.isEmpty() ? "" : " WHERE " + where);

        long chunkProcessed = 0;
        try (Connection postgresConnection = currentPostgresDataSource.getConnection();
             Connection oracleConnection = currentOracleDataSource.getConnection()) {

            postgresConnection.setAutoCommit(false);
            oracleConnection.setAutoCommit(false);

            try (PreparedStatement select = postgresConnection.prepareStatement(selectQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                int index = 1;
                if (lower != null) {
                    select.setLong(index++, lower);
                }
                if (upper != null) {
                    select.setLong(index, upper);
                }
                select.setFetchSize(CHUNK_BATCH_SIZE);

                try (ResultSet resultSet = select.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    ColumnBinder[] binders = buildBinders(metaData);
                    // Conventional-path insert: direct-path (APPEND_VALUES) takes an exclusive table lock until commit,
                    // which would serialize all parallel chunk workers writing to the same table.
                    String insertSql = buildInsertSql(tableMapping.targetTable(), metaData);

                    try (PreparedStatement insert = oracleConnection.prepareStatement(insertSql)) {
                        int batchCount = 0;
                        while (resultSet.next()) {
                            if (failed.get()) {
                                // Another chunk failed; the table is already marked as failed, stop early.
                                oracleConnection.rollback();
                                return chunkProcessed;
                            }
                            setInsertParameters(insert, resultSet, binders);
                            insert.addBatch();
                            batchCount++;

                            if (batchCount >= CHUNK_BATCH_SIZE) {
                                executeBatch(insert, oracleConnection, batchCount);
                                chunkProcessed += batchCount;
                                totalProcessed.addAndGet(batchCount);
                                batchCount = 0;
                            }
                        }
                        if (batchCount > 0) {
                            executeBatch(insert, oracleConnection, batchCount);
                            chunkProcessed += batchCount;
                            totalProcessed.addAndGet(batchCount);
                        }
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            failed.set(true);
            log.error("Chunk [{}, {}) of table {} failed: {}", lower, upper, tableMapping.sourceTable(), e.getMessage(), e);
            throw e;
        }
        return chunkProcessed;
    }

    private void migrateTable(TableMapping tableMapping) throws SQLException {
        log.info("Starting migration of table: {} -> {}", tableMapping.sourceTable(), tableMapping.targetTable());
        Instant tableStart = Instant.now();

        try (Connection postgresDataSourceConnection = currentPostgresDataSource.getConnection();
             Connection oracleDataSourceConnection = currentOracleDataSource.getConnection()) {

            postgresDataSourceConnection.setAutoCommit(false);
            oracleDataSourceConnection.setAutoCommit(false);

            String selectQuery = tableMapping.selectQuery() != null ? tableMapping.selectQuery() : "SELECT * FROM %s".formatted(tableMapping.sourceTable());

            try (PreparedStatement selectPreparedStatement = postgresDataSourceConnection.prepareStatement(selectQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                selectPreparedStatement.setFetchSize(BATCH_SIZE);
                selectPreparedStatement.setQueryTimeout(QUERY_TIMEOUT);

                try (ResultSet resultSet = selectPreparedStatement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    ColumnBinder[] binders = buildBinders(metaData);

                    String insertSql = buildInsertSql(tableMapping.targetTable(), metaData);
                    log.debug("Insert SQL: {}", insertSql);

                    try (PreparedStatement insertPreparedStatement = oracleDataSourceConnection.prepareStatement(insertSql)) {
                        int batchCount = 0;
                        long tableProcessed = 0;

                        while (resultSet.next()) {
                            setInsertParameters(insertPreparedStatement, resultSet, binders);
                            insertPreparedStatement.addBatch();
                            batchCount++;

                            if (batchCount >= BATCH_SIZE) {
                                executeBatch(insertPreparedStatement, oracleDataSourceConnection, batchCount);
                                tableProcessed += batchCount;
                                totalProcessed.addAndGet(batchCount);
                                batchCount = 0;
                            }
                        }

                        if (batchCount > 0) {
                            executeBatch(insertPreparedStatement, oracleDataSourceConnection, batchCount);
                            tableProcessed += batchCount;
                            totalProcessed.addAndGet(batchCount);
                        }

                        Duration duration = Duration.between(tableStart, Instant.now());
                        log.info("Completed migration of table {} -> {}. Records: {}, Duration: {}s", tableMapping.sourceTable(), tableMapping.targetTable(), tableProcessed, duration.getSeconds());
                    }
                }
            }
        }
    }

    private String buildInsertSql(String targetTable, ResultSetMetaData metaData) throws SQLException {
        int columnCount = metaData.getColumnCount();
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();

        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                columns.append(", ");
                placeholders.append(", ");
            }
            // Quote column names to handle Oracle reserved keywords
            columns.append("\"").append(metaData.getColumnName(i).toUpperCase()).append("\"");
            placeholders.append("?");
        }

        return String.format("INSERT INTO %s (%s) VALUES (%s)", targetTable, columns, placeholders);
    }

    /**
     * Builds one binder per column once per query, so the per-row work is a single typed read and a single typed bind
     * (no getObject + second read, no metadata lookups per cell). Conversions are the same as before:
     * booleans as 1/0, json/jsonb/uuid/arrays/other as text, bytea as bytes.
     * Nulls are bound with the column's own SQL type so the Oracle driver does not re-bind in the middle of a batch.
     */
    private ColumnBinder[] buildBinders(ResultSetMetaData metaData) throws SQLException {
        ColumnBinder[] binders = new ColumnBinder[metaData.getColumnCount()];
        for (int i = 1; i <= binders.length; i++) {
            binders[i - 1] = binderFor(metaData.getColumnType(i), metaData.getColumnTypeName(i));
        }
        return binders;
    }

    private ColumnBinder binderFor(int columnType, String columnTypeName) {
        String typeName = columnTypeName == null ? "" : columnTypeName.toLowerCase(Locale.ROOT);
        if (typeName.equals("json") || typeName.equals("jsonb") || typeName.equals("uuid") || typeName.startsWith("_")) {
            return (resultSet, insert, i) -> insert.setString(i, resultSet.getString(i));
        }
        if (typeName.equals("bytea")) {
            return (resultSet, insert, i) -> insert.setBytes(i, resultSet.getBytes(i));
        }
        if (typeName.equals("oid")) {
            return this::bindLong;
        }
        return switch (columnType) {
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> this::bindInt;
            case Types.BIGINT -> this::bindLong;
            case Types.DOUBLE, Types.FLOAT -> this::bindDouble;
            case Types.REAL -> this::bindFloat;
            case Types.BOOLEAN, Types.BIT -> this::bindBoolean;
            case Types.DECIMAL, Types.NUMERIC ->
                    (resultSet, insert, i) -> insert.setBigDecimal(i, resultSet.getBigDecimal(i));
            case Types.DATE -> (resultSet, insert, i) -> insert.setDate(i, resultSet.getDate(i));
            case Types.TIME, Types.TIME_WITH_TIMEZONE ->
                    (resultSet, insert, i) -> insert.setTime(i, resultSet.getTime(i));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                    (resultSet, insert, i) -> insert.setTimestamp(i, resultSet.getTimestamp(i));
            case Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY ->
                    (resultSet, insert, i) -> insert.setBytes(i, resultSet.getBytes(i));
            // VARCHAR, CHAR, CLOB, ARRAY, OTHER and anything else: text, as before
            default -> (resultSet, insert, i) -> insert.setString(i, resultSet.getString(i));
        };
    }

    private void bindInt(ResultSet resultSet, PreparedStatement insert, int i) throws SQLException {
        int value = resultSet.getInt(i);
        if (resultSet.wasNull()) {
            insert.setNull(i, Types.INTEGER);
        } else {
            insert.setInt(i, value);
        }
    }

    private void bindLong(ResultSet resultSet, PreparedStatement insert, int i) throws SQLException {
        long value = resultSet.getLong(i);
        if (resultSet.wasNull()) {
            insert.setNull(i, Types.BIGINT);
        } else {
            insert.setLong(i, value);
        }
    }

    private void bindDouble(ResultSet resultSet, PreparedStatement insert, int i) throws SQLException {
        double value = resultSet.getDouble(i);
        if (resultSet.wasNull()) {
            insert.setNull(i, Types.DOUBLE);
        } else {
            insert.setDouble(i, value);
        }
    }

    private void bindFloat(ResultSet resultSet, PreparedStatement insert, int i) throws SQLException {
        float value = resultSet.getFloat(i);
        if (resultSet.wasNull()) {
            insert.setNull(i, Types.REAL);
        } else {
            insert.setFloat(i, value);
        }
    }

    private void bindBoolean(ResultSet resultSet, PreparedStatement insert, int i) throws SQLException {
        boolean value = resultSet.getBoolean(i);
        if (resultSet.wasNull()) {
            insert.setNull(i, Types.INTEGER);
        } else {
            insert.setInt(i, value ? 1 : 0);
        }
    }

    private void setInsertParameters(PreparedStatement insert, ResultSet resultSet, ColumnBinder[] binders) throws SQLException {
        for (int i = 0; i < binders.length; i++) {
            binders[i].bind(resultSet, insert, i + 1);
        }
    }

    /**
     * Returns a copy of the source config whose PostgreSQL URL has prepareThreshold=-1 (binary transfer: faster reads of
     * numbers/timestamps). The request object is not changed; an explicit prepareThreshold in the URL is kept as is.
     */
    private DatabaseConfig withBinaryTransfer(DatabaseConfig source) {
        return DatabaseConfig.builder()
                .jdbcUrl(withPrepareThreshold(source.getJdbcUrl()))
                .username(source.getUsername())
                .password(source.getPassword())
                .schema(source.getSchema())
                .driverClassName(source.getDriverClassName())
                .build();
    }

    private String withPrepareThreshold(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql:") || jdbcUrl.toLowerCase(Locale.ROOT).contains("preparethreshold=")) {
            return jdbcUrl;
        }
        String separator = "?";
        if (jdbcUrl.contains("?")) {
            separator = jdbcUrl.endsWith("?") || jdbcUrl.endsWith("&") ? "" : "&";
        }
        return jdbcUrl + separator + "prepareThreshold=-1";
    }


    private void executeBatch(PreparedStatement preparedStatement, Connection connection, int batchCount) throws SQLException {
        try {
            preparedStatement.executeBatch();
            connection.commit();
            log.debug("Committed batch of {} records", batchCount);
        } catch (SQLException e) {
            connection.rollback();
            log.error("Batch execution failed, rolled back: {}", e.getMessage());
            throw e;
        }
    }

    private void logProgress(Instant start) {
        long processed = totalProcessed.get();
        long seconds = Duration.between(start, Instant.now()).getSeconds();
        long rate = seconds > 0 ? processed / seconds : 0;

        log.info("Progress: {} records ({} records/sec)", processed, rate);

        if (rate > 0 && estimatedTotal > 0) {
            long remaining = estimatedTotal - processed;
            long secondsRemaining = remaining / rate;
            log.info("Estimated time remaining: {} minutes", secondsRemaining / 60);
        }
    }

    private void logFinalStatistics(Instant start) {
        Duration duration = Duration.between(start, Instant.now());
        long processed = totalProcessed.get();
        long averageRate = duration.getSeconds() > 0 ? processed / duration.getSeconds() : 0;
        log.info("Migration Completed Successfully - Duration: {} minutes | Total records: {} | Average rate: {} records/sec", duration.toMinutes(), processed, averageRate);
    }

    private HikariDataSource createDataSource(DatabaseConfig databaseConfig, String poolName, int maxPoolSize, int minIdle) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(databaseConfig.getJdbcUrl());
        hikariConfig.setUsername(databaseConfig.getUsername());
        hikariConfig.setPassword(databaseConfig.getPassword());
        hikariConfig.setPoolName(poolName);
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setMinimumIdle(minIdle);
        hikariConfig.setConnectionTimeout(30000);

        if (databaseConfig.getDriverClassName() != null && !databaseConfig.getDriverClassName().isEmpty()) {
            hikariConfig.setDriverClassName(databaseConfig.getDriverClassName());
        }

        return new HikariDataSource(hikariConfig);
    }

    // =============================================
    // COMMON UTILITIES
    // =============================================

    private void closeDataSource(HikariDataSource dataSource) {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                log.debug("Closed datasource: {}", dataSource.getPoolName());
            } catch (Exception e) {
                log.warn("Error closing datasource: {}", e.getMessage());
            }
        }
    }

    private List<SourceSequenceInfo> getSourceSequences(Connection connection, String sourceSchema) throws SQLException {
        String query = """
                SELECT seq.relname AS sequence_name,
                       tbl.relname AS owner_table
                FROM pg_class seq
                         JOIN pg_namespace seq_ns ON seq_ns.oid = seq.relnamespace
                         LEFT JOIN pg_depend dep ON dep.objid = seq.oid
                              AND dep.deptype = 'a'
                         LEFT JOIN pg_class tbl ON tbl.oid = dep.refobjid
                         LEFT JOIN pg_namespace tbl_ns ON tbl_ns.oid = tbl.relnamespace
                WHERE seq.relkind = 'S'
                  AND seq_ns.nspname = ?
                  AND (tbl_ns.nspname = ? OR tbl_ns.nspname IS NULL)
                ORDER BY seq.relname
                """;

        List<SourceSequenceInfo> sourceSequences = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, sourceSchema);
            preparedStatement.setString(2, sourceSchema);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    sourceSequences.add(new SourceSequenceInfo(resultSet.getString("sequence_name"), resultSet.getString("owner_table")));
                }
            }
        }
        return sourceSequences;
    }

    private String resolveRelatedTableForSequence(String sequenceName, String ownerTable, Map<String, String> tableSchemaOverrides) {
        String normalizedOwnerTable = normalizeTableName(ownerTable);
        if (normalizedOwnerTable != null) {
            return normalizedOwnerTable;
        }

        if (tableSchemaOverrides == null || tableSchemaOverrides.isEmpty()) {
            return null;
        }

        String normalizedSequenceBase = normalizeSequenceBaseName(sequenceName);
        if (normalizedSequenceBase == null) {
            return null;
        }

        if (tableSchemaOverrides.containsKey(normalizedSequenceBase)) {
            return normalizedSequenceBase;
        }

        return findLongestMappedTablePrefix(normalizedSequenceBase, tableSchemaOverrides.keySet());
    }

    private String normalizeSequenceBaseName(String sequenceName) {
        if (sequenceName == null || sequenceName.isBlank()) {
            return null;
        }

        String normalized = sequenceName.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("seq_")) {
            return normalized.substring(4);
        }
        return normalized;
    }

    private String findLongestMappedTablePrefix(String sequenceBaseName, Set<String> mappedTables) {
        String bestMatch = null;
        for (String mappedTable : mappedTables) {
            if ((sequenceBaseName.equals(mappedTable) || sequenceBaseName.startsWith(mappedTable + "_")) && (bestMatch == null || mappedTable.length() > bestMatch.length())) {
                bestMatch = mappedTable;
            }
        }
        return bestMatch;
    }

    private String normalizeTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        return tableName.toLowerCase(Locale.ROOT).trim();
    }

    private String generateSequenceScript(String targetSchema, String seqName) {
        return "CREATE SEQUENCE %s.%s START WITH 1 INCREMENT BY 1;".formatted(targetSchema, seqName);
    }

    // =============================================
    // SCRIPT GENERATION HELPERS
    // =============================================

    private String generateTableScript(Connection connection,
                                       String sourceSchema,
                                       String tableName,
                                       String targetSchema,
                                       boolean hasSequence,
                                       List<String> warnings) throws SQLException {
        StringBuilder script = new StringBuilder();
        String oracleTableName = targetSchema + "." + tableName.toUpperCase(Locale.ROOT);
        String sequenceName = truncateOracleIdentifier("SEQ_" + tableName.toUpperCase(Locale.ROOT));

        List<ColumnInfo> columns = getColumns(connection, sourceSchema, tableName, warnings);
        List<String> primaryKeys = getPrimaryKeyColumns(connection, sourceSchema, tableName);

        int maxColumnNameLength = columns.stream()
                .mapToInt(columnInfo -> columnInfo.name.toUpperCase().length() + 2) // +2 for quotes
                .max()
                .orElse(0);

        List<String> typeDefs = new ArrayList<>();
        int maxTypeDefLen = 0;
        for (ColumnInfo columnInfo : columns) {
            StringBuilder typeDef = new StringBuilder();
            typeDef.append(columnInfo.oracleType);
            if (columnInfo.isSerial && hasSequence) {
                typeDef.append(DEFAULT_CLAUSE).append(targetSchema).append(".").append(sequenceName).append(".NEXTVAL");
            } else if (columnInfo.isSerial && columnInfo.sequenceName != null) {
                // Sequence is not OWNED BY the column in PostgreSQL; use the sequence referenced by the default
                String referencedSequence = truncateOracleIdentifier(columnInfo.sequenceName.toUpperCase(Locale.ROOT));
                typeDef.append(DEFAULT_CLAUSE).append(targetSchema).append(".").append(referencedSequence).append(".NEXTVAL");
            } else if (columnInfo.defaultValue != null && !columnInfo.defaultValue.isEmpty()) {
                typeDef.append(DEFAULT_CLAUSE).append(convertDefaultValue(columnInfo.defaultValue, columnInfo.oracleType));
            }
            typeDefs.add(typeDef.toString());
            if (!columnInfo.nullable && typeDef.length() > maxTypeDefLen) {
                maxTypeDefLen = typeDef.length();
            }
        }

        script.append("CREATE TABLE ").append(oracleTableName).append(LINE_SEPARATOR);
        script.append("(").append(LINE_SEPARATOR);

        List<String> columnLines = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo columnInfo = columns.get(i);
            StringBuilder columnLine = new StringBuilder();
            columnLine.append("    ");

            // Quote column names to handle Oracle reserved keywords
            String colName = "\"" + columnInfo.name.toUpperCase() + "\"";
            columnLine.append(padRight(colName, maxColumnNameLength + 2)); // +2 for the quotes
            columnLine.append(" ");

            String typeDef = typeDefs.get(i);
            if (!columnInfo.nullable) {
                columnLine.append(padRight(typeDef, maxTypeDefLen));
                columnLine.append(" NOT NULL");
            } else {
                columnLine.append(typeDef);
            }

            columnLines.add(columnLine.toString());
        }

        appendColumnsAndPrimaryKey(tableName, primaryKeys, columnLines, script);

        return script.toString();
    }

    private void appendColumnsAndPrimaryKey(String tableName, List<String> primaryKeys, List<String> columnLines, StringBuilder script) {
        if (!primaryKeys.isEmpty()) {
            String pkName = truncateOracleIdentifier("PK_" + tableName.toUpperCase(Locale.ROOT));
            // Quote column names in PRIMARY KEY constraint to handle reserved keywords
            String quotedPrimaryKeys = String.join(", ", primaryKeys.stream()
                    .map(primaryKey -> "\"" + primaryKey.toUpperCase() + "\"")
                    .toList());
            String pkLine = "    CONSTRAINT %s PRIMARY KEY (%s)".formatted(pkName, quotedPrimaryKeys);
            columnLines.add(pkLine);
        }

        for (int i = 0; i < columnLines.size(); i++) {
            script.append(columnLines.get(i));
            if (i < columnLines.size() - 1) {
                script.append(",");
            }
            script.append(LINE_SEPARATOR);
        }

        script.append(");");
    }

    private String padRight(String str, int length) {
        if (str.length() >= length) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < length) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private List<ColumnInfo> getColumns(Connection connection, String schema, String tableName, List<String> warnings) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();

        String query = """
                SELECT
                    c.column_name,
                    c.data_type,
                    c.character_maximum_length,
                    c.numeric_precision,
                    c.numeric_scale,
                    c.is_nullable,
                    c.column_default,
                    c.udt_name
                FROM information_schema.columns c
                WHERE c.table_schema = ? AND c.table_name = ?
                ORDER BY c.ordinal_position
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, schema);
            preparedStatement.setString(2, tableName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    ColumnInfo columnInfo = new ColumnInfo();
                    columnInfo.name = resultSet.getString("column_name");
                    String pgType = resultSet.getString("data_type");
                    String udtName = resultSet.getString("udt_name");
                    Integer charLength = resultSet.getObject("character_maximum_length") != null ? resultSet.getInt("character_maximum_length") : null;
                    Integer numericPrecision = resultSet.getObject("numeric_precision") != null ? resultSet.getInt("numeric_precision") : null;
                    Integer numericScale = resultSet.getObject("numeric_scale") != null ? resultSet.getInt("numeric_scale") : null;
                    columnInfo.nullable = "YES".equalsIgnoreCase(resultSet.getString("is_nullable"));
                    columnInfo.defaultValue = resultSet.getString("column_default");

                    TypeMappingContext typeMappingContext = new TypeMappingContext(pgType, udtName, charLength, numericPrecision, numericScale, columnInfo.name, tableName);
                    columnInfo.oracleType = mapPostgresToOracleType(typeMappingContext, warnings);
                    columnInfo.isSerial = columnInfo.defaultValue != null && columnInfo.defaultValue.contains("nextval");

                    if (columnInfo.isSerial) {
                        Matcher sequenceMatcher = NEXTVAL_SEQUENCE_PATTERN.matcher(columnInfo.defaultValue);
                        if (sequenceMatcher.find()) {
                            columnInfo.sequenceName = sequenceMatcher.group(1);
                        }
                        columnInfo.defaultValue = null;
                    }

                    columns.add(columnInfo);
                }
            }
        }

        return columns;
    }

    private String mapPostgresToOracleType(TypeMappingContext typeMappingContext, List<String> warnings) {
        String pgType;
        if (typeMappingContext.pgType() != null) {
            pgType = typeMappingContext.pgType().toLowerCase();
        } else if (typeMappingContext.udtName() != null) {
            pgType = typeMappingContext.udtName().toLowerCase();
        } else {
            pgType = "";
        }

        return switch (pgType) {
            case "character varying", "varchar" -> {
                int len = typeMappingContext.charLength() != null && typeMappingContext.charLength() > 0 ? Math.min(typeMappingContext.charLength(), 4000) : 255;
                yield "VARCHAR2(" + len + ")";
            }
            case "character", "char", "bpchar" -> {
                int len = typeMappingContext.charLength() != null && typeMappingContext.charLength() > 0 ? Math.min(typeMappingContext.charLength(), 2000) : 1;
                yield "CHAR(" + len + ")";
            }
            case "text", "json", "jsonb" -> "CLOB";
            case "smallint", "int2", "smallserial", "serial2" -> "NUMBER(5)";
            case "integer", "int", "int4", "serial", "serial4", "oid" -> "NUMBER(10)";
            case "bigint", "int8", "bigserial", "serial8" -> "NUMBER(19)";
            case "numeric", "decimal" -> {
                if (typeMappingContext.numericPrecision() != null && typeMappingContext.numericScale() != null) {
                    yield "NUMBER(" + typeMappingContext.numericPrecision() + "," + typeMappingContext.numericScale() + ")";
                } else if (typeMappingContext.numericPrecision() != null) {
                    yield "NUMBER(" + typeMappingContext.numericPrecision() + ")";
                }
                yield "NUMBER";
            }
            case "real", "float4" -> "BINARY_FLOAT";
            case "double precision", "float8" -> "BINARY_DOUBLE";
            case "money" -> "NUMBER(19,4)";
            case "boolean", "bool" -> "NUMBER(1)";
            case "date" -> "DATE";
            case "time", "time without time zone", "timestamp", "timestamp without time zone" -> "TIMESTAMP";
            case "time with time zone", "timetz", "timestamp with time zone", "timestamptz" ->
                    "TIMESTAMP WITH TIME ZONE";
            case "interval" -> "INTERVAL DAY TO SECOND";
            case "bytea" -> "BLOB";
            case "uuid" -> "VARCHAR2(36)";
            case "xml" -> "XMLTYPE";
            case "inet", "cidr", "macaddr", "macaddr8" -> {
                warnings.add(String.format("Table %s, column %s: PostgreSQL type '%s' mapped to VARCHAR2(45)", typeMappingContext.tableName(), typeMappingContext.columnName(), pgType));
                yield "VARCHAR2(45)";
            }
            case "point", "line", "lseg", "box", "path", "polygon", "circle" -> {
                warnings.add(String.format("Table %s, column %s: PostgreSQL geometric type '%s' mapped to VARCHAR2(1000)", typeMappingContext.tableName(), typeMappingContext.columnName(), pgType));
                yield "VARCHAR2(1000)";
            }
            case "tsvector", "tsquery" -> {
                warnings.add(String.format("Table %s, column %s: PostgreSQL full-text type '%s' mapped to CLOB", typeMappingContext.tableName(), typeMappingContext.columnName(), pgType));
                yield "CLOB";
            }
            case "array", "user-defined" -> {
                if (typeMappingContext.udtName() != null && typeMappingContext.udtName().startsWith("_")) {
                    warnings.add(String.format("Table %s, column %s: PostgreSQL array type mapped to CLOB", typeMappingContext.tableName(), typeMappingContext.columnName()));
                    yield "CLOB";
                }
                warnings.add(String.format("Table %s, column %s: PostgreSQL type '%s' (%s) mapped to VARCHAR2(4000)", typeMappingContext.tableName(), typeMappingContext.columnName(), pgType, typeMappingContext.udtName()));
                yield "VARCHAR2(4000)";
            }
            case "bit", "bit varying", "varbit" -> {
                int len = typeMappingContext.charLength() != null && typeMappingContext.charLength() > 0 ? (typeMappingContext.charLength() + 7) / 8 : 1;
                yield "RAW(" + Math.clamp(len, 1, 2000) + ")";
            }
            default -> {
                warnings.add(String.format("Table %s, column %s: Unknown PostgreSQL type '%s' mapped to VARCHAR2(4000)", typeMappingContext.tableName(), typeMappingContext.columnName(), pgType));
                yield "VARCHAR2(4000)";
            }
        };
    }

    private String convertDefaultValue(String pgDefault, String oracleType) {
        if (pgDefault == null || pgDefault.isEmpty()) {
            return null;
        }

        if (pgDefault.contains("nextval")) {
            return null;
        }

        if (oracleType.equals("NUMBER(1)")) {
            if (pgDefault.equalsIgnoreCase("true")) {
                return "1";
            } else if (pgDefault.equalsIgnoreCase("false")) {
                return "0";
            }
        }

        if (pgDefault.equalsIgnoreCase("CURRENT_TIMESTAMP") || pgDefault.equalsIgnoreCase("now()") || pgDefault.equalsIgnoreCase("CURRENT_DATE")) {
            return "SYSTIMESTAMP";
        }

        if (pgDefault.contains("::")) {
            return pgDefault.substring(0, pgDefault.indexOf("::"));
        }

        return pgDefault;
    }

    private List<String> getPrimaryKeyColumns(Connection connection, String schema, String tableName) throws SQLException {
        List<String> primaryKeyColumns = new ArrayList<>();

        String query = """
                SELECT a.attname
                FROM pg_index i
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
                JOIN pg_class c ON c.oid = i.indrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE i.indisprimary
                  AND n.nspname = ?
                  AND c.relname = ?
                ORDER BY array_position(i.indkey, a.attnum)
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, schema);
            preparedStatement.setString(2, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    primaryKeyColumns.add(resultSet.getString("attname"));
                }
            }
        }

        return primaryKeyColumns;
    }

    private List<String> generateIndexScripts(Connection connection, String sourceSchema, String tableName, String targetSchema) throws SQLException {
        List<String> scripts = new ArrayList<>();

        String query = """
                SELECT
                    i.relname AS index_name,
                    pg_get_indexdef(i.oid) AS index_def
                FROM pg_index ix
                JOIN pg_class t ON t.oid = ix.indrelid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = ?
                  AND t.relname = ?
                  AND NOT ix.indisprimary
                ORDER BY i.relname
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, sourceSchema);
            preparedStatement.setString(2, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String indexName = resultSet.getString("index_name");
                    String indexDef = resultSet.getString("index_def");

                    if (indexDef == null || indexDef.isBlank()) {
                        continue;
                    }

                    String script = indexDef;
                    String targetQualifiedTable = targetSchema + "." + tableName.toUpperCase();

                    script = script.replaceFirst("(?i)CREATE\\s+(UNIQUE\\s+)?INDEX\\s+\"?" + Pattern.quote(indexName) + "\"?", "CREATE $1INDEX " + targetSchema + "." + indexName.toUpperCase());
                    script = script.replaceFirst("(?i)\\sON\\s+\"?" + Pattern.quote(sourceSchema) + "\"?\\.\"?" + Pattern.quote(tableName) + "\"?", " ON " + targetQualifiedTable);
                    script = script.replaceFirst("(?i)\\sON\\s+\"?" + Pattern.quote(tableName) + "\"?", " ON " + targetQualifiedTable);
                    // Oracle does not support PostgreSQL's USING <access_method> clause (e.g., USING btree)
                    script = USING_PATTERN.matcher(script).replaceFirst("");

                    if (!script.trim().endsWith(";")) {
                        script += ";";
                    }

                    scripts.add(script);
                }
            }
        }

        return scripts;
    }

    private List<String> generateForeignKeyScripts(Connection connection,
                                                   String sourceSchema,
                                                   String tableName,
                                                   String targetSchema,
                                                   Map<String, String> tableSchemaOverrides,
                                                   String defaultTargetSchema,
                                                   Set<String> crossSchemaReferenceGrants) throws SQLException {
        List<String> scripts = new ArrayList<>();

        String query = """
                SELECT con.conname     AS constraint_name,
                       att.attname     AS column_name,
                       ref_cl.relname  AS foreign_table_name,
                       ref_att.attname AS foreign_column_name
                FROM pg_constraint con
                         JOIN pg_class cl ON cl.oid = con.conrelid
                         JOIN pg_namespace ns ON ns.oid = cl.relnamespace
                         JOIN pg_class ref_cl ON ref_cl.oid = con.confrelid
                         CROSS JOIN LATERAL generate_subscripts(con.conkey, 1) AS ord(n)
                                JOIN pg_attribute att     ON att.attrelid = con.conrelid     AND att.attnum = con.conkey[ord.n]
                                JOIN pg_attribute ref_att ON ref_att.attrelid = con.confrelid AND ref_att.attnum = con.confkey[ord.n]
                                WHERE con.contype = 'f'
                                  AND ns.nspname = ?
                                  AND cl.relname = ?
                                ORDER BY con.conname, ord.n
                """;

        Map<String, ForeignKeyInfo> foreignKeyInfoMap = new LinkedHashMap<>();

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, sourceSchema);
            preparedStatement.setString(2, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String constraintName = resultSet.getString("constraint_name");
                    String columnName = resultSet.getString("column_name");
                    String refTable = resultSet.getString("foreign_table_name");
                    String refColumn = resultSet.getString("foreign_column_name");

                    ForeignKeyInfo fkInfo = foreignKeyInfoMap.computeIfAbsent(constraintName, ignored -> {
                        ForeignKeyInfo info = new ForeignKeyInfo();
                        info.constraintName = constraintName;
                        info.refTable = refTable;
                        return info;
                    });
                    fkInfo.columns.add(columnName);
                    fkInfo.refColumns.add(refColumn);
                }
            }
        }

        int foreignKeyCounter = 1;
        for (ForeignKeyInfo foreignKeyInfo : foreignKeyInfoMap.values()) {
            String oracleForeignKeyInfoName = truncateOracleIdentifier("FK_%s_%02d".formatted(tableName.toUpperCase(Locale.ROOT), foreignKeyCounter++));
            String refTargetSchema = resolveTargetSchemaForTable(foreignKeyInfo.refTable, tableSchemaOverrides, defaultTargetSchema);

            String columns = String.join(", ", foreignKeyInfo.columns.stream()
                    .map(String::toUpperCase)
                    .toList());
            String refColumns = String.join(", ", foreignKeyInfo.refColumns.stream()
                    .map(String::toUpperCase)
                    .toList());
            String script = "ALTER TABLE %s.%s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s.%s (%s) DEFERRABLE INITIALLY DEFERRED;"
                    .formatted(targetSchema, tableName.toUpperCase(Locale.ROOT), oracleForeignKeyInfoName, columns, refTargetSchema, foreignKeyInfo.refTable.toUpperCase(Locale.ROOT), refColumns);

            if (!targetSchema.equalsIgnoreCase(refTargetSchema)) {
                crossSchemaReferenceGrants.add("GRANT REFERENCES ON %s.%s TO %s;".formatted(refTargetSchema, foreignKeyInfo.refTable.toUpperCase(Locale.ROOT), targetSchema));
            }

            scripts.add(script);
        }

        return scripts;
    }

    private String truncateOracleIdentifier(String identifier) {
        if (identifier.length() > ORACLE_MAX_IDENTIFIER_LENGTH) {
            log.warn("Oracle identifier '{}' exceeds {} chars and will be truncated", identifier, ORACLE_MAX_IDENTIFIER_LENGTH);
            return identifier.substring(0, ORACLE_MAX_IDENTIFIER_LENGTH);
        }
        return identifier;
    }

    private Map<String, String> buildTableSchemaOverrides(Map<String, List<String>> tableSchemaMap, String operation) {
        if (tableSchemaMap == null || tableSchemaMap.isEmpty()) {
            return Map.of();
        }

        Map<String, String> tableToSchema = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : tableSchemaMap.entrySet()) {
            String normalizedSchema = normalizeSchemaKey(entry.getKey(), operation);
            addTablesToSchemaMapping(tableToSchema, normalizedSchema, entry.getValue(), operation);
        }

        return tableToSchema;
    }

    private String normalizeSchemaKey(String targetSchema, String operation) {
        if (targetSchema == null || targetSchema.isBlank()) {
            throw new IllegalArgumentException("Invalid tableSchemaMap for " + operation + ": schema key cannot be blank.");
        }
        return targetSchema.toUpperCase(Locale.ROOT);
    }

    private void addTablesToSchemaMapping(Map<String, String> tableToSchema,
                                          String normalizedSchema,
                                          List<String> tables,
                                          String operation) {
        if (tables == null || tables.isEmpty()) {
            return;
        }

        for (String table : tables) {
            if (table == null || table.isBlank()) {
                continue;
            }

            String normalizedTable = table.toLowerCase(Locale.ROOT);
            String previous = tableToSchema.putIfAbsent(normalizedTable, normalizedSchema);
            if (previous != null && !previous.equals(normalizedSchema)) {
                throw new IllegalArgumentException("Invalid tableSchemaMap for " + operation + ": table '" + table + "' is assigned to multiple schemas ('" + previous + "', '" + normalizedSchema + "').");
            }
        }
    }

    private String resolveTargetSchemaForTable(String tableName, Map<String, String> tableSchemaOverrides, String defaultTargetSchema) {
        if (tableName != null && tableSchemaOverrides != null && !tableSchemaOverrides.isEmpty()) {
            String resolved = tableSchemaOverrides.get(tableName.toLowerCase(Locale.ROOT));
            if (resolved != null) {
                return resolved;
            }
        }
        return defaultTargetSchema;
    }

    private Set<String> resolveAllTargetSchemas(String defaultSchema, Map<String, String> tableSchemaOverrides) {
        if (tableSchemaOverrides != null && !tableSchemaOverrides.isEmpty()) {
            return new LinkedHashSet<>(tableSchemaOverrides.values());
        }
        Set<String> schemas = new LinkedHashSet<>();
        schemas.add(defaultSchema);
        return schemas;
    }

    private String resolveDefaultSchema(Map<String, String> tableSchemaOverrides, String fallbackSchema) {
        if (tableSchemaOverrides != null && !tableSchemaOverrides.isEmpty()) {
            return tableSchemaOverrides.values().iterator().next();
        }
        return fallbackSchema;
    }

    @FunctionalInterface
    private interface ColumnBinder {
        void bind(ResultSet resultSet, PreparedStatement insert, int index) throws SQLException;
    }

    private record TableMapping(String sourceTable, String targetTable, String targetSchema, String selectQuery) {
        public TableMapping(String sourceTable, String targetTable, String targetSchema) {
            this(sourceTable, targetTable, targetSchema, null);
        }
    }

    private record ChunkPlan(String pkColumn, long min, long max) {
    }

    /**
     * PK range [lower, upper); null means unbounded on that side.
     */
    private record ChunkRange(Long lower, Long upper) {
    }

    private record PrimaryKeyIndex(String owner, String constraintName, String indexName, String columns) {
    }

    private record RolePair(String editRole, String viewRole) {
    }

    private record TypeMappingContext(String pgType,
                                      String udtName,
                                      Integer charLength,
                                      Integer numericPrecision,
                                      Integer numericScale,
                                      String columnName,
                                      String tableName) {
    }

    private record SourceSequenceInfo(String sequenceName, String ownerTable) {
    }

    private static class ColumnInfo {
        String name;
        String oracleType;
        boolean nullable;
        String defaultValue;
        boolean isSerial;
        String sequenceName;
    }

    private static class ForeignKeyInfo {
        String constraintName;
        List<String> columns = new ArrayList<>();
        String refTable;
        List<String> refColumns = new ArrayList<>();
    }

    /**
     * Context class to hold all script generation processing state.
     */
    private class ScriptContext {
        final ScriptGenerationRequest scriptGenerationRequest;
        final Connection connection;
        final String sourceSchema;
        final String targetSchema;
        final boolean mapBasedMode;
        final Map<String, String> tableToTargetSchema;
        final List<String> tablesToProcess;
        final List<String> warnings = new ArrayList<>();
        final Set<String> warnedFallbackTables = new HashSet<>();
        final Set<String> tablesWithManagedSequences = new HashSet<>();
        final List<String> sequenceScripts = new ArrayList<>();
        final List<String> tableScripts = new ArrayList<>();
        final List<String> foreignKeyScripts = new ArrayList<>();
        final Map<String, List<String>> schemaSequenceMap = new LinkedHashMap<>();
        final Map<String, List<String>> schemaTableMap = new LinkedHashMap<>();
        final Map<String, List<String>> tableIndexMap = new LinkedHashMap<>();
        final Set<String> crossSchemaReferenceGrants = new LinkedHashSet<>();

        ScriptContext(ScriptGenerationRequest scriptGenerationRequest, Connection connection) throws SQLException {
            this.scriptGenerationRequest = scriptGenerationRequest;
            this.connection = connection;
            this.sourceSchema = scriptGenerationRequest.getSource().getSchema();
            this.mapBasedMode = scriptGenerationRequest.getTableSchemaMap() != null && !scriptGenerationRequest.getTableSchemaMap().isEmpty();
            this.tableToTargetSchema = mapBasedMode ? buildTableSchemaOverrides(scriptGenerationRequest.getTableSchemaMap(), "generate-scripts") : Map.of();
            this.targetSchema = resolveDefaultSchema(this.tableToTargetSchema, scriptGenerationRequest.getTargetSchema().toUpperCase(Locale.ROOT));

            List<String> sourceTables = getTableNamesStatic(connection, sourceSchema);
            if (mapBasedMode) {
                this.tablesToProcess = sourceTables.stream()
                        .filter(table -> tableToTargetSchema.containsKey(table.toLowerCase(Locale.ROOT)))
                        .toList();
            } else {
                this.tablesToProcess = sourceTables;
            }

            for (String table : tablesToProcess) {
                String resolvedTargetSchema = resolveTargetSchema(table);
                schemaTableMap.computeIfAbsent(resolvedTargetSchema, ignored -> new ArrayList<>()).add(table);
            }
        }

        private static List<String> getTableNamesStatic(Connection connection, String schema) throws SQLException {
            List<String> tables = new ArrayList<>();
            String query = """
                    SELECT tablename
                    FROM pg_catalog.pg_tables
                    WHERE schemaname = ?
                    ORDER BY tablename
                    """;
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, schema);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        tables.add(resultSet.getString("tablename"));
                    }
                }
            }
            return tables;
        }

        private String resolveTargetSchema(String tableName) {
            if (tableName != null && tableToTargetSchema != null && !tableToTargetSchema.isEmpty()) {
                String normalizedTable = tableName.toLowerCase(Locale.ROOT);
                String resolved = tableToTargetSchema.get(normalizedTable);
                if (resolved != null) {
                    return resolved;
                }

                if (warnedFallbackTables.add(normalizedTable)) {
                    warnings.add("Table '%s' is not present in tableSchemaMap. Falling back to default target schema '%s'.".formatted(tableName, targetSchema));
                }
            }
            return targetSchema;
        }
    }
}
