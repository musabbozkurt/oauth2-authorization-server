package mb.oauth2authorizationserver.config.jpa;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.io.Serial;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OracleInClauseInspector implements StatementInspector {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Pattern IN_CLAUSE_PATTERN = Pattern.compile("\\b(in)\\s*\\(\\s*\\?\\s*(,\\s*\\?\\s*)*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_EXTRACT_PATTERN = Pattern.compile("([A-Z0-9_.]+)\\s+$", Pattern.CASE_INSENSITIVE);

    @Override
    public String inspect(String sql) {
        if (sql == null || !sql.toLowerCase().contains("in")) {
            return sql;
        }

        Matcher matcher = IN_CLAUSE_PATTERN.matcher(sql);
        StringBuilder stringBuilder = new StringBuilder();

        while (matcher.find()) {
            String inClause = matcher.group();
            long paramCount = inClause.chars().filter(ch -> ch == '?').count();

            // Intercept lists larger than 999 items
            if (paramCount > 999) {
                String fieldName = extractFieldName(sql, matcher.start());

                if (!fieldName.isEmpty()) {
                    // GENERIC FIX: Convert the IN clause to join against an Oracle Varray table type.
                    // This forces Oracle to read it as a single block, keeping your EntityGraph intact.
                    String rewrittenClause = "IN (SELECT column_value FROM TABLE(sys.odcinumberlist(" + generateQuestionMarks((int) paramCount) + ")))";

                    matcher.appendReplacement(stringBuilder, Matcher.quoteReplacement(rewrittenClause));
                    continue;
                }
            }
            matcher.appendReplacement(stringBuilder, Matcher.quoteReplacement(inClause));
        }
        matcher.appendTail(stringBuilder);
        return stringBuilder.toString();
    }

    private String extractFieldName(String sql, int inIndex) {
        String leftOfIn = sql.substring(0, inIndex);
        leftOfIn = leftOfIn.replaceAll("[,(]\\s*$", "").trim();

        Matcher columnMatcher = COLUMN_EXTRACT_PATTERN.matcher(leftOfIn);
        if (columnMatcher.find()) {
            return columnMatcher.group(1);
        }
        return "";
    }

    private String generateQuestionMarks(int total) {
        StringBuilder queryPlaceholder = new StringBuilder();
        for (int i = 0; i < total; i++) {
            queryPlaceholder.append("?");
            if (i < total - 1) {
                queryPlaceholder.append(",");
            }
        }
        return queryPlaceholder.toString();
    }
}
