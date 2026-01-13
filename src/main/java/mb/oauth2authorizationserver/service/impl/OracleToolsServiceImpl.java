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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service implementation for Oracle database tools including:
 * - DDL/DCL script generation from PostgreSQL schema
 * - PostgreSQL to Oracle data migration
 */
@Slf4j
@Service
public class OracleToolsServiceImpl implements OracleToolsService {

    // =============================================
    // CONSTANTS
    // =============================================

    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String SECTION_SEPARATOR = "-- =============================================";

    // Migration constants
    private static final long MIGRATION_LOCK_KEY = 123456789L;
    private static final int BATCH_SIZE = 10000;
    private static final int THREAD_COUNT = 10;
    private static final int PROGRESS_INTERVAL_SECONDS = 30;
    private static final int QUERY_TIMEOUT = 600;

    // Migration state
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private volatile long estimatedTotal = 0;
    private HikariDataSource currentOracleDataSource;
    private HikariDataSource currentPostgresDataSource;
    private String currentSourceSchema;
    private String currentTargetSchema;

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
        try (Connection conn = dataSource.getConnection()) {
            ScriptContext scriptContext = new ScriptContext(request, conn);
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
            processSequence(scriptContext, tableName);
            processTableDdl(scriptContext, tableName);
            scriptContext.tableIndexMap.put(tableName, generateIndexScripts(scriptContext.conn, scriptContext.sourceSchema, tableName, scriptContext.targetSchema));
            scriptContext.foreignKeyScripts.addAll(generateForeignKeyScripts(scriptContext.conn, scriptContext.sourceSchema, tableName, scriptContext.targetSchema));
        } catch (Exception e) {
            String warning = String.format("Error processing table %s: %s", tableName, e.getMessage());
            log.warn(warning);
            scriptContext.warnings.add(warning);
        }
    }

    private void processSequence(ScriptContext scriptContext, String tableName) throws SQLException {
        String seqName = getSequenceNameIfExists(scriptContext.conn, scriptContext.sourceSchema, tableName);
        if (seqName != null) {
            scriptContext.sequenceScripts.add(generateSequenceScript(scriptContext.targetSchema, seqName));
            scriptContext.processedSequences.add(seqName);
        }
    }

    private void processTableDdl(ScriptContext scriptContext, String tableName) throws SQLException {
        String tableScript = generateTableScript(scriptContext.conn, scriptContext.sourceSchema, tableName, scriptContext.targetSchema, scriptContext.processedSequences, scriptContext.warnings);
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
        String roleBaseName = deriveRoleBaseName(scriptContext.targetSchema);
        String editRole = scriptContext.request.getEditRoleName() != null ? scriptContext.request.getEditRoleName() : "%s_EDIT_ROLE".formatted(roleBaseName);
        String viewRole = scriptContext.request.getViewRoleName() != null ? scriptContext.request.getViewRoleName() : "%s_VIEW_ROLE".formatted(roleBaseName);
        Set<String> editRoleUsers = CollectionUtils.isNotEmpty(scriptContext.request.getEditRoleUsers()) ? scriptContext.request.getEditRoleUsers() : Set.of("myapp_user");
        Set<String> viewRoleUsers = CollectionUtils.isNotEmpty(scriptContext.request.getViewRoleUsers()) ? scriptContext.request.getViewRoleUsers() : Set.of("myapp_user");

        appendDclHeader(dcl);
        appendRoleCreation(dcl, editRole, viewRole);
        appendSequenceGrants(dcl, scriptContext.processedSequences, scriptContext.targetSchema, editRole);
        appendTableGrants(dcl, scriptContext.tablesToProcess, scriptContext.targetSchema, editRole, viewRole);
        appendRoleAssignments(dcl, editRole, viewRole, editRoleUsers, viewRoleUsers);

        return dcl.toString();
    }

    /**
     * Derives the role base name from the target schema.
     * Examples:
     * - mb_oracle_schema -> MB_ORACLE_SCHEMA
     * - mb_oracle_schema_env -> MB_ORACLE_SCHEMA
     * - NEXTHEACX_WIZAS -> HEACX
     */
    private String deriveRoleBaseName(String targetSchema) {
        if (targetSchema == null || targetSchema.isEmpty()) {
            return "DEFAULT";
        }

        String baseName = targetSchema.toUpperCase();

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

    private void appendRoleCreation(StringBuilder dcl, String editRole, String viewRole) {
        dcl.append("-- Roller").append(LINE_SEPARATOR);
        dcl.append("CREATE ROLE ").append(editRole).append(";").append(LINE_SEPARATOR);
        dcl.append("CREATE ROLE ").append(viewRole).append(";").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
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
        try {
            totalProcessed.set(0);
            estimatedTotal = 0;

            DatabaseConfig source = request.getSource();
            DatabaseConfig destination = request.getDestination();
            currentPostgresDataSource = createDataSource(source, "PostgresPool", 30, 5);
            currentOracleDataSource = createDataSource(destination, "OraclePool", 30, 5);
            currentSourceSchema = source.getSchema();
            currentTargetSchema = destination.getSchema();

            log.info("Created datasources - Source: {} (schema: {}), Destination: {} (schema: {})", source.getJdbcUrl(), currentSourceSchema, destination.getJdbcUrl(), currentTargetSchema);

            executeMigration();
        } finally {
            closeDataSource(currentPostgresDataSource);
            closeDataSource(currentOracleDataSource);
        }
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

            log.info("Acquired migration lock. Starting PostgreSQL to Oracle migration...");
            performMigration();
        } catch (SQLException e) {
            log.error("Failed to acquire migration lock. Exception: {}", ExceptionUtils.getStackTrace(e));
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        } finally {
            releaseLock(lockConnection);
        }
    }

    private boolean acquireLock(Connection conn) throws SQLException {
        String lockQuery = "SELECT pg_try_advisory_lock(?)";
        try (PreparedStatement preparedStatement = conn.prepareStatement(lockQuery)) {
            preparedStatement.setLong(1, MIGRATION_LOCK_KEY);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private void releaseLock(Connection conn) {
        if (conn != null) {
            try {
                String unlockQuery = "SELECT pg_advisory_unlock(?)";
                try (PreparedStatement preparedStatement = conn.prepareStatement(unlockQuery)) {
                    preparedStatement.setLong(1, MIGRATION_LOCK_KEY);
                    preparedStatement.executeQuery();
                }
                conn.close();
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

        try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
             ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor()) {

            for (TableMapping mapping : tableMappings) {
                executor.submit(() -> {
                    try {
                        migrateTable(mapping);
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
        log.info("Updating all sequences to latest record values in schema {}...", currentTargetSchema);

        String findSequencesQuery = """
                SELECT sequence_name
                FROM all_sequences
                WHERE sequence_owner = ?
                ORDER BY sequence_name
                """;

        try (Connection conn = currentOracleDataSource.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(findSequencesQuery)) {

            preparedStatement.setString(1, currentTargetSchema);
            int updatedCount = 0;
            int skippedCount = 0;

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String sequenceName = resultSet.getString("sequence_name");
                    String tableName = deriveTableNameFromSequence(sequenceName);

                    if (tableName != null) {
                        boolean updated = updateSequenceToMaxValue(sequenceName, tableName);
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

            log.info("Completed updating sequences in schema {} - Updated: {}, Skipped: {}", currentTargetSchema, updatedCount, skippedCount);
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

    private boolean updateSequenceToMaxValue(String sequenceName, String tableName) {
        String fullTableName = currentTargetSchema + "." + tableName;
        String fullSequenceName = currentTargetSchema + "." + sequenceName;

        try (Connection conn = currentOracleDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String checkTableQuery = "SELECT COUNT(*) FROM all_tables WHERE owner = '%s' AND table_name = '%s'".formatted(currentTargetSchema, tableName);
            try (ResultSet resultSet = stmt.executeQuery(checkTableQuery)) {
                if (resultSet.next() && resultSet.getInt(1) == 0) {
                    log.debug("Table {} does not exist, skipping sequence {}", fullTableName, fullSequenceName);
                    return false;
                }
            }

            String checkColumnQuery = "SELECT COUNT(*) FROM all_tab_columns WHERE owner = '%s' AND table_name = '%s' AND column_name = '%s'".formatted(currentTargetSchema, tableName, "ID");
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
                        resultSet.next(); // Consume the result
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
        log.info("Disabling all foreign key constraints in schema {}...", currentTargetSchema);
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
                """.formatted(currentTargetSchema, currentTargetSchema);

        executeOraclePLSQL(sql, "disable foreign keys");
    }

    private void enableAllForeignKeys() {
        log.info("Enabling all foreign key constraints in schema {}...", currentTargetSchema);
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
                """.formatted(currentTargetSchema, currentTargetSchema);

        executeOraclePLSQL(sql, "enable foreign keys");
    }

    private void executeOraclePLSQL(String sql, String operation) {
        try (Connection conn = currentOracleDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("Successfully executed: {}", operation);
        } catch (SQLException e) {
            log.error("Failed to {}: {}", operation, e.getMessage(), e);
        }
    }

    private List<TableMapping> getTableMappingsForMigration() {
        List<TableMapping> mappings = new ArrayList<>();

        String query = """
                SELECT schemaname, tablename
                FROM pg_catalog.pg_tables
                WHERE schemaname = ?
                ORDER BY tablename
                """;

        try (Connection conn = currentPostgresDataSource.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(query)) {

            preparedStatement.setString(1, currentSourceSchema);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String schema = resultSet.getString("schemaname");
                    String tableName = resultSet.getString("tablename");
                    String sourceTable = "%s.%s".formatted(schema, tableName);
                    String targetTable = "%s.%s".formatted(currentTargetSchema, tableName.toUpperCase());

                    mappings.add(new TableMapping(sourceTable, targetTable));
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
            String countQuery = "SELECT COUNT(*) FROM %s".formatted(mapping.sourceTable());
            try (Connection conn = currentPostgresDataSource.getConnection();
                 PreparedStatement preparedStatement = conn.prepareStatement(countQuery);
                 ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    total += resultSet.getLong(1);
                }
            } catch (SQLException e) {
                log.warn("Could not get count for table {}: {}", mapping.sourceTable(), e.getMessage());
            }
        }
        log.info("Estimated total records to migrate: {}", total);
        return total;
    }

    private void migrateTable(TableMapping mapping) throws SQLException {
        log.info("Starting migration of table: {} -> {}", mapping.sourceTable(), mapping.targetTable());
        Instant tableStart = Instant.now();

        try (Connection pgConn = currentPostgresDataSource.getConnection();
             Connection oraConn = currentOracleDataSource.getConnection()) {

            pgConn.setAutoCommit(false);
            oraConn.setAutoCommit(false);

            String selectQuery = mapping.selectQuery() != null ? mapping.selectQuery() : "SELECT * FROM %s".formatted(mapping.sourceTable());

            try (PreparedStatement selectPreparedStatement = pgConn.prepareStatement(selectQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                selectPreparedStatement.setFetchSize(BATCH_SIZE);
                selectPreparedStatement.setQueryTimeout(QUERY_TIMEOUT);

                try (ResultSet resultSet = selectPreparedStatement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    String insertSql = buildInsertSql(mapping.targetTable(), metaData);
                    log.debug("Insert SQL: {}", insertSql);

                    try (PreparedStatement insertPreparedStatement = oraConn.prepareStatement(insertSql)) {
                        int batchCount = 0;
                        long tableProcessed = 0;

                        while (resultSet.next()) {
                            setInsertParameters(insertPreparedStatement, resultSet, metaData, columnCount);
                            insertPreparedStatement.addBatch();
                            batchCount++;

                            if (batchCount >= BATCH_SIZE) {
                                executeBatch(insertPreparedStatement, oraConn, batchCount);
                                tableProcessed += batchCount;
                                totalProcessed.addAndGet(batchCount);
                                batchCount = 0;
                            }
                        }

                        if (batchCount > 0) {
                            executeBatch(insertPreparedStatement, oraConn, batchCount);
                            tableProcessed += batchCount;
                            totalProcessed.addAndGet(batchCount);
                        }

                        Duration duration = Duration.between(tableStart, Instant.now());
                        log.info("Completed migration of table {} -> {}. Records: {}, Duration: {}s", mapping.sourceTable(), mapping.targetTable(), tableProcessed, duration.getSeconds());
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
            columns.append(metaData.getColumnName(i).toUpperCase());
            placeholders.append("?");
        }

        return String.format("INSERT INTO %s (%s) VALUES (%s)", targetTable, columns, placeholders);
    }

    private void setInsertParameters(PreparedStatement insertPreparedStatement, ResultSet resultSet, ResultSetMetaData metaData, int columnCount) throws SQLException {
        for (int i = 1; i <= columnCount; i++) {
            int columnType = metaData.getColumnType(i);
            String columnTypeName = metaData.getColumnTypeName(i);
            Object value = resultSet.getObject(i);

            if (value == null) {
                insertPreparedStatement.setNull(i, Types.VARCHAR);
            } else {
                if ("jsonb".equalsIgnoreCase(columnTypeName) || "json".equalsIgnoreCase(columnTypeName)) {
                    insertPreparedStatement.setString(i, value.toString());
                } else if (columnTypeName != null && columnTypeName.startsWith("_")) {
                    insertPreparedStatement.setString(i, value.toString());
                } else if ("uuid".equalsIgnoreCase(columnTypeName)) {
                    insertPreparedStatement.setString(i, value.toString());
                } else if ("bytea".equalsIgnoreCase(columnTypeName)) {
                    insertPreparedStatement.setBytes(i, resultSet.getBytes(i));
                } else if ("oid".equalsIgnoreCase(columnTypeName)) {
                    insertPreparedStatement.setLong(i, resultSet.getLong(i));
                } else {
                    setStandardTypeParameter(insertPreparedStatement, resultSet, i, columnType, columnTypeName, value, metaData);
                }
            }
        }
    }

    private void setStandardTypeParameter(PreparedStatement insertPreparedStatement, ResultSet resultSet, int i, int columnType, String columnTypeName, Object value, ResultSetMetaData metaData) throws SQLException {
        switch (columnType) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.NCHAR ->
                    insertPreparedStatement.setString(i, resultSet.getString(i));
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> insertPreparedStatement.setInt(i, resultSet.getInt(i));
            case Types.BIGINT -> insertPreparedStatement.setLong(i, resultSet.getLong(i));
            case Types.DOUBLE, Types.FLOAT -> insertPreparedStatement.setDouble(i, resultSet.getDouble(i));
            case Types.REAL -> insertPreparedStatement.setFloat(i, resultSet.getFloat(i));
            case Types.DECIMAL, Types.NUMERIC -> insertPreparedStatement.setBigDecimal(i, resultSet.getBigDecimal(i));
            case Types.DATE -> insertPreparedStatement.setDate(i, resultSet.getDate(i));
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> insertPreparedStatement.setTime(i, resultSet.getTime(i));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                    insertPreparedStatement.setTimestamp(i, resultSet.getTimestamp(i));
            case Types.BOOLEAN, Types.BIT -> insertPreparedStatement.setInt(i, resultSet.getBoolean(i) ? 1 : 0);
            case Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY ->
                    insertPreparedStatement.setBytes(i, resultSet.getBytes(i));
            case Types.CLOB, Types.NCLOB -> insertPreparedStatement.setString(i, resultSet.getString(i));
            case Types.ARRAY, Types.OTHER -> insertPreparedStatement.setString(i, value.toString());
            default -> {
                try {
                    insertPreparedStatement.setString(i, value.toString());
                } catch (Exception _) {
                    log.warn("Could not convert column {} (type: {}, typeName: {}) to string", metaData.getColumnName(i), columnType, columnTypeName);
                    insertPreparedStatement.setObject(i, value);
                }
            }
        }
    }

    private void executeBatch(PreparedStatement preparedStatement, Connection conn, int batchCount) throws SQLException {
        try {
            preparedStatement.executeBatch();
            conn.commit();
            log.debug("Committed batch of {} records", batchCount);
        } catch (SQLException e) {
            conn.rollback();
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
        long avgRate = duration.getSeconds() > 0 ? processed / duration.getSeconds() : 0;
        log.info("Migration Completed Successfully - Duration: {} minutes | Total records: {} | Average rate: {} records/sec", duration.toMinutes(), processed, avgRate);
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

    private String getSequenceNameIfExists(Connection conn, String sourceSchema, String tableName) throws SQLException {
        String query = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND column_default LIKE 'nextval%'
                LIMIT 1
                """;

        try (PreparedStatement preparedStatement = conn.prepareStatement(query)) {
            preparedStatement.setString(1, sourceSchema);
            preparedStatement.setString(2, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return "SEQ_" + tableName.toUpperCase();
                }
            }
        }
        return null;
    }

    private String generateSequenceScript(String targetSchema, String seqName) {
        return "CREATE SEQUENCE %s.%s START WITH 1 INCREMENT BY 1;".formatted(targetSchema, seqName);
    }

    // =============================================
    // SCRIPT GENERATION HELPERS
    // =============================================

    private String generateTableScript(Connection conn,
                                       String sourceSchema,
                                       String tableName,
                                       String targetSchema,
                                       List<String> processedSequences,
                                       List<String> warnings) throws SQLException {
        StringBuilder script = new StringBuilder();
        String oracleTableName = targetSchema + "." + tableName.toUpperCase();
        String seqName = "SEQ_" + tableName.toUpperCase();
        boolean hasSequence = processedSequences.contains(seqName);

        List<ColumnInfo> columns = getColumns(conn, sourceSchema, tableName, warnings);
        List<String> primaryKeys = getPrimaryKeyColumns(conn, sourceSchema, tableName);

        int maxColNameLen = columns.stream()
                .mapToInt(col -> col.name.toUpperCase().length())
                .max()
                .orElse(0);

        List<String> typeDefs = new ArrayList<>();
        int maxTypeDefLen = 0;
        for (ColumnInfo col : columns) {
            StringBuilder typeDef = new StringBuilder();
            typeDef.append(col.oracleType);
            if (col.isSerial && hasSequence) {
                typeDef.append(" DEFAULT ").append(targetSchema).append(".").append(seqName).append(".NEXTVAL");
            } else if (col.defaultValue != null && !col.defaultValue.isEmpty()) {
                typeDef.append(" DEFAULT ").append(convertDefaultValue(col.defaultValue, col.oracleType));
            }
            typeDefs.add(typeDef.toString());
            if (!col.nullable && typeDef.length() > maxTypeDefLen) {
                maxTypeDefLen = typeDef.length();
            }
        }

        script.append("CREATE TABLE ").append(oracleTableName).append(LINE_SEPARATOR);
        script.append("(").append(LINE_SEPARATOR);

        List<String> columnLines = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            StringBuilder colLine = new StringBuilder();
            colLine.append("    ");

            String colName = col.name.toUpperCase();
            colLine.append(padRight(colName, maxColNameLen));
            colLine.append(" ");

            String typeDef = typeDefs.get(i);
            if (!col.nullable) {
                colLine.append(padRight(typeDef, maxTypeDefLen));
                colLine.append(" NOT NULL");
            } else {
                colLine.append(typeDef);
            }

            columnLines.add(colLine.toString());
        }

        appendColumnsAndPrimaryKey(tableName, primaryKeys, columnLines, script);

        return script.toString();
    }

    private void appendColumnsAndPrimaryKey(String tableName, List<String> primaryKeys, List<String> columnLines, StringBuilder script) {
        if (!primaryKeys.isEmpty()) {
            String pkName = "PK_" + tableName.toUpperCase();
            String pkLine = "    CONSTRAINT %s PRIMARY KEY (%s)".formatted(pkName, String.join(", ", primaryKeys.stream().map(String::toUpperCase).toList()));
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

    private List<ColumnInfo> getColumns(Connection conn, String schema, String tableName, List<String> warnings) throws SQLException {
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

        try (PreparedStatement preparedStatement = conn.prepareStatement(query)) {
            preparedStatement.setString(1, schema);
            preparedStatement.setString(2, tableName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    ColumnInfo col = new ColumnInfo();
                    col.name = resultSet.getString("column_name");
                    String pgType = resultSet.getString("data_type");
                    String udtName = resultSet.getString("udt_name");
                    Integer charLength = resultSet.getObject("character_maximum_length") != null ? resultSet.getInt("character_maximum_length") : null;
                    Integer numPrecision = resultSet.getObject("numeric_precision") != null ? resultSet.getInt("numeric_precision") : null;
                    Integer numScale = resultSet.getObject("numeric_scale") != null ? resultSet.getInt("numeric_scale") : null;
                    col.nullable = "YES".equalsIgnoreCase(resultSet.getString("is_nullable"));
                    col.defaultValue = resultSet.getString("column_default");

                    TypeMappingContext typeMappingContext = new TypeMappingContext(pgType, udtName, charLength, numPrecision, numScale, col.name, tableName);
                    col.oracleType = mapPostgresToOracleType(typeMappingContext, warnings);
                    col.isSerial = col.defaultValue != null && col.defaultValue.contains("nextval");

                    if (col.isSerial) {
                        col.defaultValue = null;
                    }

                    columns.add(col);
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
                if (typeMappingContext.numPrecision() != null && typeMappingContext.numScale() != null) {
                    yield "NUMBER(" + typeMappingContext.numPrecision() + "," + typeMappingContext.numScale() + ")";
                } else if (typeMappingContext.numPrecision() != null) {
                    yield "NUMBER(" + typeMappingContext.numPrecision() + ")";
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

    private List<String> getPrimaryKeyColumns(Connection conn, String schema, String tableName) throws SQLException {
        List<String> pkColumns = new ArrayList<>();

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

        try (PreparedStatement preparedStatement = conn.prepareStatement(query)) {
            preparedStatement.setString(1, schema);
            preparedStatement.setString(2, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    pkColumns.add(resultSet.getString("attname"));
                }
            }
        }

        return pkColumns;
    }

    private List<String> generateIndexScripts(Connection conn, String sourceSchema, String tableName, String targetSchema) throws SQLException {
        List<String> scripts = new ArrayList<>();

        String query = """
                SELECT
                    i.relname AS index_name,
                    ix.indisunique AS is_unique,
                    array_agg(a.attname ORDER BY array_position(ix.indkey, a.attnum)) AS columns
                FROM pg_index ix
                JOIN pg_class t ON t.oid = ix.indrelid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
                WHERE n.nspname = ?
                  AND t.relname = ?
                  AND NOT ix.indisprimary
                GROUP BY i.relname, ix.indisunique
                ORDER BY i.relname
                """;

        try (PreparedStatement preparedStatement = conn.prepareStatement(query)) {
            preparedStatement.setString(1, sourceSchema);
            preparedStatement.setString(2, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String indexName = resultSet.getString("index_name").toUpperCase();
                    boolean isUnique = resultSet.getBoolean("is_unique");
                    String[] columns = (String[]) resultSet.getArray("columns").getArray();

                    String script = "CREATE %sINDEX %s.%s ON %s.%s (%s);"
                            .formatted(isUnique ? "UNIQUE " : "", targetSchema, indexName, targetSchema, tableName.toUpperCase(), String.join(", ", java.util.Arrays.stream(columns).map(String::toUpperCase).toList()));

                    scripts.add(script);
                }
            }
        }

        return scripts;
    }

    private List<String> generateForeignKeyScripts(Connection conn, String sourceSchema, String tableName, String targetSchema) throws SQLException {
        List<String> scripts = new ArrayList<>();

        String query = """
                SELECT tc.constraint_name,
                       kcu.column_name,
                       ccu.table_name  AS foreign_table_name,
                       ccu.column_name AS foreign_column_name
                FROM information_schema.table_constraints AS tc
                         JOIN information_schema.key_column_usage AS kcu
                              ON tc.constraint_name = kcu.constraint_name
                                  AND tc.table_schema = kcu.table_schema
                         JOIN information_schema.constraint_column_usage AS ccu
                              ON ccu.constraint_name = tc.constraint_name
                                  AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = ?
                  AND tc.table_name = ?
                ORDER BY tc.constraint_name
                """;

        Map<String, ForeignKeyInfo> fkMap = new LinkedHashMap<>();

        try (PreparedStatement preparedStatement = conn.prepareStatement(query)) {
            preparedStatement.setString(1, sourceSchema);
            preparedStatement.setString(2, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String constraintName = resultSet.getString("constraint_name");
                    String columnName = resultSet.getString("column_name");
                    String refTable = resultSet.getString("foreign_table_name");
                    String refColumn = resultSet.getString("foreign_column_name");

                    ForeignKeyInfo fkInfo = fkMap.computeIfAbsent(constraintName, k -> {
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

        for (ForeignKeyInfo fkInfo : fkMap.values()) {
            String oracleFkName = "FK_%s_%s".formatted(tableName.toUpperCase(), fkInfo.refTable.toUpperCase());
            if (oracleFkName.length() > 30) {
                oracleFkName = oracleFkName.substring(0, 30);
            }

            String script = "ALTER TABLE %s.%s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s.%s (%s);"
                    .formatted(targetSchema, tableName.toUpperCase(), oracleFkName, String.join(", ", fkInfo.columns.stream().map(String::toUpperCase).toList()), targetSchema, fkInfo.refTable.toUpperCase(), String.join(", ", fkInfo.refColumns.stream().map(String::toUpperCase).toList()));

            scripts.add(script);
        }

        return scripts;
    }

    /**
     * Context class to hold all script generation processing state.
     */
    private static class ScriptContext {
        final ScriptGenerationRequest request;
        final Connection conn;
        final String sourceSchema;
        final String targetSchema;
        final List<String> tablesToProcess;
        final List<String> warnings = new ArrayList<>();
        final List<String> processedSequences = new ArrayList<>();
        final List<String> sequenceScripts = new ArrayList<>();
        final List<String> tableScripts = new ArrayList<>();
        final List<String> foreignKeyScripts = new ArrayList<>();
        final Map<String, List<String>> tableIndexMap = new LinkedHashMap<>();

        ScriptContext(ScriptGenerationRequest request, Connection conn) throws SQLException {
            this.request = request;
            this.conn = conn;
            this.sourceSchema = request.getSource().getSchema();
            this.targetSchema = request.getTargetSchema();
            this.tablesToProcess = getTableNamesStatic(conn, sourceSchema);
        }

        private static List<String> getTableNamesStatic(Connection conn, String schema) throws SQLException {
            List<String> tables = new ArrayList<>();
            String query = """
                    SELECT tablename
                    FROM pg_catalog.pg_tables
                    WHERE schemaname = ?
                    ORDER BY tablename
                    """;
            try (PreparedStatement preparedStatement = conn.prepareStatement(query)) {
                preparedStatement.setString(1, schema);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        tables.add(resultSet.getString("tablename"));
                    }
                }
            }
            return tables;
        }
    }

    private record TableMapping(String sourceTable, String targetTable, String selectQuery) {
        public TableMapping(String sourceTable, String targetTable) {
            this(sourceTable, targetTable, null);
        }
    }

    private record TypeMappingContext(String pgType,
                                      String udtName,
                                      Integer charLength,
                                      Integer numPrecision,
                                      Integer numScale,
                                      String columnName,
                                      String tableName) {
    }

    private static class ColumnInfo {
        String name;
        String oracleType;
        boolean nullable;
        String defaultValue;
        boolean isSerial;
    }

    private static class ForeignKeyInfo {
        String constraintName;
        List<String> columns = new ArrayList<>();
        String refTable;
        List<String> refColumns = new ArrayList<>();
    }
}
