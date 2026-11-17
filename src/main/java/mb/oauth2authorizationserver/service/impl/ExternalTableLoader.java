package mb.oauth2authorizationserver.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;

import javax.sql.DataSource;
import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads a large table through an Oracle external table, using only the JDBC drivers already on the classpath:
 * <ol>
 *     <li>Postgres COPY exports PK-range chunks in parallel into files under {@code exportPath}.
 *     That path must be the same storage the Oracle DIRECTORY points to (shared mount).</li>
 *     <li>One direct-path, parallel INSERT ... SELECT from an external table over those files loads the target in a single
 *     transaction: the target is either fully loaded or stays empty, so there is never partial data or duplication.</li>
 *     <li>The external table and the files are removed.</li>
 * </ol>
 * Records are separated by 0x1E instead of newline so text values that contain line breaks are loaded unchanged.
 */
@Slf4j
final class ExternalTableLoader {

    private static final byte RECORD_SEPARATOR = 0x1E;
    private static final String DATETIME_MASK = "YYYY-MM-DD HH24:MI:SS.FF6";
    private static final String PG_DATETIME_MASK = "YYYY-MM-DD HH24:MI:SS.US";
    private static final int READ_SIZE = 16 * 1024 * 1024;
    private static final int CLOB_FIELD_LENGTH = 15_000_000;
    private static final int DEFAULT_FIELD_LENGTH = 4000;
    private static final int WRITE_BUFFER_SIZE = 1 << 20;
    private static final Pattern ORACLE_IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_$#]{0,127}");
    private static final Set<String> UNSUPPORTED_ORACLE_TYPES = Set.of("BLOB", "RAW", "LONG RAW", "BFILE", "LONG");
    private static final Set<String> NUMERIC_ORACLE_TYPES = Set.of("NUMBER", "FLOAT", "INTEGER", "BINARY_FLOAT", "BINARY_DOUBLE");
    private static final Set<String> CLOB_ORACLE_TYPES = Set.of("CLOB", "NCLOB");
    private static final String TIMESTAMP = "TIMESTAMP";
    private static final String TEXT_CAST = "::text";
    private static final String PG_COLUMNS_SQL = """
            SELECT a.attname, t.typname
            FROM pg_attribute a
                     JOIN pg_type t ON t.oid = a.atttypid
            WHERE a.attrelid = '%s'::regclass AND a.attnum > 0 AND NOT a.attisdropped
            ORDER BY a.attnum
            """;
    private static final String ORACLE_COLUMNS_SQL = """
            SELECT column_name, data_type, data_length
            FROM all_tab_columns
            WHERE owner = ? AND table_name = ?
            """;
    private final DataSource postgres;
    private final DataSource oracle;
    private final String directory;
    private final Path exportDir;
    private final int parallelDegree;
    private final String zone;
    private final boolean zoneIsOffset;

    ExternalTableLoader(DataSource postgres, DataSource oracle, String directory, String exportPath, int parallelDegree) {
        if (!ORACLE_IDENTIFIER.matcher(directory.trim()).matches()) {
            throw new IllegalArgumentException("Invalid Oracle directory name: " + directory);
        }
        this.postgres = postgres;
        this.oracle = oracle;
        this.directory = directory.trim().toUpperCase(Locale.ROOT);
        this.exportDir = Path.of(exportPath.trim());
        this.parallelDegree = parallelDegree;
        // Same wall-clock semantics as the JDBC path, which reads/binds timestamps in the JVM default time zone.
        ZoneId zoneId = ZoneId.systemDefault().normalized();
        this.zoneIsOffset = zoneId instanceof ZoneOffset;
        this.zone = zoneId instanceof ZoneOffset offset && offset.getTotalSeconds() == 0 ? "+00:00" : zoneId.getId();
    }

    static boolean isEnabled(String directory, String exportPath) {
        return directory != null && !directory.isBlank() && exportPath != null && !exportPath.isBlank();
    }

    /**
     * Fails fast (before any data is touched) when Oracle cannot read files written to exportPath through the directory.
     */
    void verifyOracleCanReadExportPath() throws SQLException, IOException {
        Files.createDirectories(exportDir);
        String name = newExternalTableName();
        Path probe = exportDir.resolve(name.toLowerCase(Locale.ROOT) + ".dat");
        Files.write(probe, new byte[]{'1', RECORD_SEPARATOR});
        try (Connection connection = oracle.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE %s ("V" VARCHAR2(10))
                    ORGANIZATION EXTERNAL (
                      TYPE ORACLE_LOADER DEFAULT DIRECTORY "%s"
                      ACCESS PARAMETERS (RECORDS DELIMITED BY 0X'1E' NOBADFILE NOLOGFILE NODISCARDFILE FIELDS TERMINATED BY ',' ("V" CHAR(10)))
                      LOCATION ('%s')
                    ) REJECT LIMIT 0
                    """.formatted(name, directory, probe.getFileName()));
            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + name)) {
                if (!resultSet.next() || resultSet.getLong(1) != 1) {
                    throw new SQLException("Oracle directory %s does not show the probe file written to %s".formatted(directory, exportDir));
                }
            } finally {
                dropQuietly(connection, name);
            }
        } catch (SQLException e) {
            throw new SQLException("Oracle cannot read files written to %s through directory %s. exportPath must be the same (shared) storage as the Oracle directory and the app user needs READ, WRITE on it. Cause: %s".formatted(exportDir, directory, e.getMessage()), e);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    /**
     * Returns null when the table has binary columns (bytea / BLOB / RAW), which are left to the JDBC path.
     */
    TableLayout describe(String sourceTable, String targetSchema, String targetTableName, String pkColumn) throws SQLException {
        Map<String, String[]> oracleColumns = oracleColumns(targetSchema, targetTableName);
        List<ColumnPlan> columns = new ArrayList<>();
        try (Connection connection = postgres.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(PG_COLUMNS_SQL.formatted(sourceTable));
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                String pgName = resultSet.getString(1);
                String pgType = resultSet.getString(2);
                String oracleName = pgName.toUpperCase(Locale.ROOT);
                String[] target = oracleColumns.get(oracleName);
                if (target == null) {
                    throw new SQLException("Column %s does not exist in %s.%s".formatted(oracleName, targetSchema, targetTableName));
                }
                if ("bytea".equals(pgType) || UNSUPPORTED_ORACLE_TYPES.contains(target[0])) {
                    return null;
                }
                columns.add(new ColumnPlan(pgName, pgType, oracleName, target[0], Integer.parseInt(target[1])));
            }
        }
        return new TableLayout(sourceTable, targetSchema + "." + targetTableName, pkColumn, columns);
    }

    /**
     * Exports all ranges in parallel, then loads them with one atomic direct-path insert. Always removes the
     * external table and data files; the Oracle log file is kept only when the load fails (for diagnosis).
     */
    long load(TableLayout layout, List<ChunkRange> ranges, ExecutorService executor, AtomicLong progress) throws SQLException {
        String name = newExternalTableName();
        String filePrefix = name.toLowerCase(Locale.ROOT);
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            files.add(exportDir.resolve("%s_%d.dat".formatted(filePrefix, i)));
        }
        Path logFile = exportDir.resolve(filePrefix + ".log");
        AtomicLong counted = new AtomicLong();
        LongConsumer onRowsExported = rows -> {
            counted.addAndGet(rows);
            progress.addAndGet(rows);
        };
        boolean loaded = false;
        try {
            Instant exportStart = Instant.now();
            long exported = export(layout, ranges, files, executor, onRowsExported);
            log.info("Exported {} rows of {} into {} files in {}s. Loading into {} with direct-path parallel insert.", exported, layout.sourceTable(), files.size(), Duration.between(exportStart, Instant.now()).getSeconds(), layout.targetTable());

            Instant loadStart = Instant.now();
            long rows = insertFromExternalTable(layout, name, files, logFile, exported);
            log.info("Loaded {} rows into {} in {}s.", rows, layout.targetTable(), Duration.between(loadStart, Instant.now()).getSeconds());
            loaded = true;
            return rows;
        } finally {
            if (!loaded) {
                // Nothing was committed, so don't report the exported rows as migrated.
                progress.addAndGet(-counted.get());
            }
            cleanup(name, files, loaded ? logFile : null);
        }
    }

    private long export(TableLayout layout, List<ChunkRange> ranges, List<Path> files, ExecutorService executor, LongConsumer onRowsExported) throws SQLException {
        String select = buildSelect(layout);
        AtomicBoolean failed = new AtomicBoolean(false);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            ChunkRange range = ranges.get(i);
            Path file = files.get(i);
            futures.add(executor.submit(() -> exportChunk(select, layout.pkColumn(), range, file, failed, onRowsExported)));
        }

        long total = 0;
        Throwable firstError = null;
        for (Future<Long> future : futures) {
            try {
                total += future.get();
            } catch (ExecutionException e) {
                failed.set(true);
                firstError = firstError == null ? e.getCause() : firstError;
            } catch (InterruptedException e) {
                failed.set(true);
                futures.forEach(f -> f.cancel(true));
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while exporting " + layout.sourceTable(), e);
            }
        }
        if (firstError != null) {
            throw new SQLException("Export of %s failed, target %s was not touched. Cause: %s".formatted(layout.sourceTable(), layout.targetTable(), firstError.getMessage()), firstError);
        }
        return total;
    }

    private long exportChunk(String select, String pkColumn, ChunkRange range, Path file, AtomicBoolean failed, LongConsumer onRowsExported) throws SQLException, IOException {
        if (failed.get()) {
            return 0;
        }
        String copySql = "COPY (%s%s) TO STDOUT WITH (FORMAT csv)".formatted(select, rangeFilter(pkColumn, range));
        try (Connection connection = postgres.getConnection();
             OutputStream out = new RecordSeparatorOutputStream(new BufferedOutputStream(Files.newOutputStream(file), WRITE_BUFFER_SIZE), failed)) {
            long rows = connection.unwrap(PGConnection.class).getCopyAPI().copyOut(copySql, out);
            onRowsExported.accept(rows);
            return rows;
        } catch (SQLException | IOException | RuntimeException e) {
            failed.set(true);
            throw e;
        }
    }

    private String rangeFilter(String pkColumn, ChunkRange range) {
        String quotedPk = quotePg(pkColumn);
        List<String> conditions = new ArrayList<>();
        if (range.lower() != null) {
            conditions.add("%s >= %d".formatted(quotedPk, range.lower()));
        }
        if (range.upper() != null) {
            conditions.add("%s < %d".formatted(quotedPk, range.upper()));
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    // ---------------------------------------------------------------- export (Postgres COPY)

    private String buildSelect(TableLayout layout) {
        String expressions = layout.columns().stream().map(this::pgExpression).collect(Collectors.joining(", "));
        return "SELECT %s FROM %s".formatted(expressions, layout.sourceTable());
    }

    /**
     * Values are written in the same shape the JDBC path binds them: booleans as 1/0, date/time in the JVM time zone
     * as 'YYYY-MM-DD HH24:MI:SS.US', everything else as Postgres text.
     */
    private String pgExpression(ColumnPlan column) {
        String c = quotePg(column.pgName());
        boolean datetimeTarget = isDatetime(column.oracleType());
        return switch (column.pgType()) {
            case "bool" -> "CASE WHEN %s THEN 1 WHEN NOT %s THEN 0 END".formatted(c, c);
            case "timestamp" -> datetimeTarget ? "to_char(%s, '%s')".formatted(c, PG_DATETIME_MASK) : c + TEXT_CAST;
            case "timestamptz" ->
                    datetimeTarget ? "to_char(%s AT TIME ZONE %s, '%s')".formatted(c, pgZone(), PG_DATETIME_MASK) : c + TEXT_CAST;
            case "date" ->
                    datetimeTarget ? "to_char(%s::timestamp, '%s')".formatted(c, PG_DATETIME_MASK) : c + TEXT_CAST;
            case "time" ->
                    datetimeTarget ? "to_char(DATE '1970-01-01' + %s, '%s')".formatted(c, PG_DATETIME_MASK) : c + TEXT_CAST;
            default -> c + TEXT_CAST;
        };
    }

    private String pgZone() {
        return zoneIsOffset ? "INTERVAL '%s' HOUR TO MINUTE".formatted(zone) : "'%s'".formatted(zone);
    }

    private long insertFromExternalTable(TableLayout layout, String name, List<Path> files, Path logFile, long expectedRows) throws SQLException {
        try (Connection connection = oracle.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createExternalTableSql(layout, name, files, logFile));
            statement.execute("ALTER SESSION ENABLE PARALLEL DML");
            statement.execute("ALTER SESSION SET NLS_NUMERIC_CHARACTERS = '.,'");
            connection.setAutoCommit(false);
            try {
                long rows = statement.executeLargeUpdate(insertSql(layout, name));
                if (rows != expectedRows) {
                    throw new SQLException("Row count mismatch for %s: exported %d, loaded %d. Rolled back.".formatted(layout.targetTable(), expectedRows, rows));
                }
                connection.commit();
                return rows;
            } catch (SQLException e) {
                connection.rollback();
                throw new SQLException("Load into %s failed and was rolled back (target is empty). Oracle log: %s. Cause: %s".formatted(layout.targetTable(), logFile, e.getMessage()), e);
            } finally {
                connection.setAutoCommit(true);
                resetSession(statement);
            }
        }
    }

    private void resetSession(Statement statement) {
        try {
            statement.execute("ALTER SESSION DISABLE PARALLEL DML");
        } catch (SQLException e) {
            log.debug("Could not disable parallel DML on pooled connection: {}", e.getMessage());
        }
    }

    private String createExternalTableSql(TableLayout layout, String name, List<Path> files, Path logFile) {
        String columns = layout.columns().stream()
                .map(column -> "\"%s\" %s".formatted(column.oracleName(), externalColumnType(column)))
                .collect(Collectors.joining(", "));
        String fields = layout.columns().stream()
                .map(column -> "\"%s\" CHAR(%d)".formatted(column.oracleName(), fieldLength(column)))
                .collect(Collectors.joining(", "));
        String locations = files.stream().map(file -> "'" + file.getFileName() + "'").collect(Collectors.joining(", "));
        return """
                CREATE TABLE %s (%s)
                ORGANIZATION EXTERNAL (
                  TYPE ORACLE_LOADER
                  DEFAULT DIRECTORY "%s"
                  ACCESS PARAMETERS (
                    RECORDS DELIMITED BY 0X'1E' CHARACTERSET AL32UTF8
                    NOBADFILE NODISCARDFILE LOGFILE '%s'
                    READSIZE %d
                    FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' NOTRIM
                    MISSING FIELD VALUES ARE NULL
                    (%s)
                  )
                  LOCATION (%s)
                )
                PARALLEL %d
                REJECT LIMIT 0
                """.formatted(name, columns, directory, logFile.getFileName(), READ_SIZE, fields, locations, parallelDegree);
    }

    // ---------------------------------------------------------------- load (Oracle external table)

    private String insertSql(TableLayout layout, String name) {
        String targetColumns = layout.columns().stream().map(column -> "\"" + column.oracleName() + "\"").collect(Collectors.joining(", "));
        String values = layout.columns().stream().map(this::oracleExpression).collect(Collectors.joining(", "));
        return "INSERT /*+ APPEND PARALLEL(t, %d) */ INTO %s t (%s) SELECT /*+ PARALLEL(e, %d) */ %s FROM %s e"
                .formatted(parallelDegree, layout.targetTable(), targetColumns, parallelDegree, values, name);
    }

    private String oracleExpression(ColumnPlan column) {
        String value = "e.\"" + column.oracleName() + "\"";
        String type = column.oracleType();
        if (type.startsWith(TIMESTAMP) && type.contains("TIME ZONE")) {
            return "FROM_TZ(TO_TIMESTAMP(%s, '%s'), '%s')".formatted(value, DATETIME_MASK, zone);
        }
        if (type.startsWith(TIMESTAMP)) {
            return "TO_TIMESTAMP(%s, '%s')".formatted(value, DATETIME_MASK);
        }
        if ("DATE".equals(type)) {
            return "CAST(TO_TIMESTAMP(%s, '%s') AS DATE)".formatted(value, DATETIME_MASK);
        }
        if (NUMERIC_ORACLE_TYPES.contains(type)) {
            return "TO_NUMBER(" + value + ")";
        }
        return value;
    }

    private String externalColumnType(ColumnPlan column) {
        if (CLOB_ORACLE_TYPES.contains(column.oracleType())) {
            return "CLOB";
        }
        return "VARCHAR2(%d)".formatted(Math.max(DEFAULT_FIELD_LENGTH, column.oracleLength()));
    }

    private int fieldLength(ColumnPlan column) {
        return CLOB_ORACLE_TYPES.contains(column.oracleType()) ? CLOB_FIELD_LENGTH : Math.max(DEFAULT_FIELD_LENGTH, column.oracleLength());
    }

    private boolean isDatetime(String oracleType) {
        return "DATE".equals(oracleType) || oracleType.startsWith(TIMESTAMP);
    }

    private Map<String, String[]> oracleColumns(String owner, String tableName) throws SQLException {
        Map<String, String[]> columns = new HashMap<>();
        try (Connection connection = oracle.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(ORACLE_COLUMNS_SQL)) {
            preparedStatement.setString(1, owner);
            preparedStatement.setString(2, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    columns.put(resultSet.getString(1), new String[]{resultSet.getString(2), String.valueOf(resultSet.getInt(3))});
                }
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Target table %s.%s not found".formatted(owner, tableName));
        }
        return columns;
    }

    private void cleanup(String name, List<Path> files, Path logFile) {
        try (Connection connection = oracle.getConnection()) {
            dropQuietly(connection, name);
        } catch (SQLException e) {
            log.warn("Could not drop external table {}: {}", name, e.getMessage());
        }
        List<Path> toDelete = new ArrayList<>(files);
        if (logFile != null) {
            toDelete.add(logFile);
        }
        for (Path file : toDelete) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Could not delete {}: {}", file, e.getMessage());
            }
        }
    }

    private void dropQuietly(Connection connection, String name) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + name + " PURGE");
        } catch (SQLException e) {
            log.debug("Drop of {} skipped: {}", name, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- helpers

    private String newExternalTableName() {
        return "MIGX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private String quotePg(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    record ChunkRange(Long lower, Long upper) {
    }

    record TableLayout(String sourceTable, String targetTable, String pkColumn, List<ColumnPlan> columns) {
    }

    record ColumnPlan(String pgName, String pgType, String oracleName, String oracleType, int oracleLength) {
    }

    /**
     * Turns Postgres CSV into 0x1E-separated records: a newline outside quotes ends a record, a newline inside a quoted
     * value is data. UTF-8 continuation bytes never equal '"' or '\n', so a byte scan is safe. Aborts when another
     * chunk has failed.
     */
    static final class RecordSeparatorOutputStream extends FilterOutputStream {
        private final AtomicBoolean failed;
        private boolean inQuotes;

        RecordSeparatorOutputStream(OutputStream out, AtomicBoolean failed) {
            super(out);
            this.failed = failed;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        /**
         * pgjdbc calls this once per exported row; bytes are passed through in place (no copy), only record-ending
         * newlines are replaced.
         */
        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (failed.get()) {
                throw new IOException("Export aborted because another chunk failed");
            }
            int end = offset + length;
            int start = offset;
            for (int i = offset; i < end; i++) {
                if (bytes[i] == '"') {
                    inQuotes = !inQuotes;
                } else if (bytes[i] == '\n' && !inQuotes) {
                    out.write(bytes, start, i - start);
                    out.write(RECORD_SEPARATOR);
                    start = i + 1;
                }
            }
            out.write(bytes, start, end - start);
        }
    }
}
