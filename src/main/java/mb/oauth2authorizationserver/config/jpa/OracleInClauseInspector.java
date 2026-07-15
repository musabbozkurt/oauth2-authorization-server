package mb.oauth2authorizationserver.config.jpa;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.io.Serial;

/**
 * Rewrites large Hibernate {@code IN (? , ? , ...)} clauses before they are sent to Oracle.
 * <p>
 * Oracle rejects {@code IN} lists with more than 1000 expressions. When a clause exceeds the configured
 * limit, it is converted to use an Oracle table function such as {@code sys.odcinumberlist}.
 * Large lists are split into multiple function calls because Oracle also limits function arguments.
 * <p>
 * Safety rules:
 * <ul>
 *   <li>Only pure placeholder lists like {@code IN (?, ?, ?)} are rewritten</li>
 *   <li>Subqueries, literals, and already rewritten clauses are left unchanged</li>
 *   <li>When parsing is uncertain, the original SQL is preserved</li>
 * </ul>
 */
public class OracleInClauseInspector implements StatementInspector {

    @Serial
    private static final long serialVersionUID = 1L;

    private final OracleInClauseRewriteConfig config;

    public OracleInClauseInspector() {
        this(OracleInClauseRewriteConfig.defaults());
    }

    public OracleInClauseInspector(OracleInClauseRewriteConfig config) {
        this.config = config;
    }

    @Override
    public String inspect(String sql) {
        if (sql == null || !config.enabled() || !sql.toLowerCase().contains("in")) {
            return sql;
        }

        StringBuilder result = new StringBuilder(sql.length());
        int cursor = 0;

        while (cursor < sql.length()) {
            InKeywordMatch keyword = findNextInKeyword(sql, cursor);
            if (keyword == null) {
                result.append(sql, cursor, sql.length());
                cursor = sql.length();
            } else {
                InClauseMatch match = parseInClause(sql, keyword);
                if (match == null) {
                    result.append(sql, cursor, keyword.start() + 1);
                    cursor = keyword.start() + 1;
                } else {
                    result.append(sql, cursor, match.keywordStart());

                    if (shouldRewrite(sql, match)) {
                        appendRewrittenInClause(result, sql, match);
                    } else {
                        result.append(sql, match.keywordStart(), match.end());
                    }

                    cursor = match.end();
                }
            }
        }

        return result.toString();
    }

    private boolean shouldRewrite(String sql, InClauseMatch match) {
        if (!match.placeholderList()) {
            return false;
        }
        if (match.paramCount() <= config.maxParameterCount()) {
            return false;
        }
        return !config.requireColumnExpression() || !extractColumnExpression(sql, match.keywordStart()).isEmpty();
    }

    /**
     * Rewrites one large placeholder list into one or more {@code TABLE(oracleFunction(...))} clauses.
     * <p>
     * {@code IN} chunks are combined with {@code OR}. {@code NOT IN} chunks are combined with {@code AND}.
     */
    private void appendRewrittenInClause(StringBuilder result, String sql, InClauseMatch match) {
        String columnExpression = extractColumnExpression(sql, match.keywordStart());
        boolean negated = isNotInKeyword(sql, match.keywordStart());
        int chunkSize = config.oracleFunctionMaxArgumentCount();
        int placeholderCount = match.paramCount();
        int chunkCount = (placeholderCount + chunkSize - 1) / chunkSize;

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            if (chunkIndex > 0) {
                result.append(negated ? " AND " : " OR ");
                result.append(columnExpression).append(' ');
                if (negated) {
                    result.append("NOT ");
                }
                result.append("IN ");
            } else {
                result.append(sql, match.keywordStart(), match.listOpenParen());
            }

            int chunkPlaceholderCount = Math.min(chunkSize, placeholderCount - (chunkIndex * chunkSize));
            result.append("(SELECT column_value FROM TABLE(")
                    .append(config.oracleTableFunction())
                    .append("(")
                    .append(generateQuestionMarks(chunkPlaceholderCount))
                    .append(")))");
        }
    }

    private static boolean isNotInKeyword(String sql, int keywordStart) {
        if (keywordStart < 0 || keywordStart + 3 > sql.length()) {
            return false;
        }
        if (!regionMatchesIgnoreCase(sql, keywordStart, "not")) {
            return false;
        }
        int inStart = skipWhitespace(sql, keywordStart + 3);
        return regionMatchesIgnoreCase(sql, inStart, "in");
    }

    private static InKeywordMatch findNextInKeyword(String sql, int from) {
        for (int index = from; index < sql.length(); index++) {
            InKeywordMatch notInKeyword = readNotInKeyword(sql, index);
            if (notInKeyword != null) {
                return notInKeyword;
            }
            InKeywordMatch inKeyword = readInKeyword(sql, index);
            if (inKeyword != null) {
                return inKeyword;
            }
        }
        return null;
    }

    private static InKeywordMatch readNotInKeyword(String sql, int index) {
        if (!regionMatchesIgnoreCase(sql, index, "not")) {
            return null;
        }
        int inStart = skipWhitespace(sql, index + 3);
        if (!regionMatchesIgnoreCase(sql, inStart, "in")) {
            return null;
        }
        if (hasKeywordBoundaryBefore(sql, index)) {
            return null;
        }
        int keywordEnd = inStart + 2;
        if (hasKeywordBoundaryAfter(sql, keywordEnd)) {
            return null;
        }
        return new InKeywordMatch(index, keywordEnd);
    }

    private static InKeywordMatch readInKeyword(String sql, int index) {
        if (!regionMatchesIgnoreCase(sql, index, "in")) {
            return null;
        }
        if (hasKeywordBoundaryBefore(sql, index)) {
            return null;
        }
        if (hasKeywordBoundaryAfter(sql, index + 2)) {
            return null;
        }
        return new InKeywordMatch(index, index + 2);
    }

    private InClauseMatch parseInClause(String sql, InKeywordMatch keyword) {
        int listOpenParen = skipWhitespace(sql, keyword.end());
        if (listOpenParen >= sql.length() || sql.charAt(listOpenParen) != '(') {
            return null;
        }

        int contentStart = skipWhitespace(sql, listOpenParen + 1);
        if (contentStart >= sql.length()) {
            return null;
        }

        if (sql.charAt(contentStart) == ')') {
            return new InClauseMatch(keyword.start(), listOpenParen, contentStart + 1, 0, true);
        }

        if (isAlreadyRewrittenClause(sql, listOpenParen, contentStart)) {
            int clauseEnd = findMatchingClosingParen(sql, listOpenParen);
            return clauseEnd < 0 ? null : new InClauseMatch(keyword.start(), listOpenParen, clauseEnd, 0, false);
        }

        if (!isPlaceholderListStart(sql, contentStart)) {
            int clauseEnd = findMatchingClosingParen(sql, listOpenParen);
            return clauseEnd < 0 ? null : new InClauseMatch(keyword.start(), listOpenParen, clauseEnd, 0, false);
        }

        return parsePlaceholderList(sql, keyword.start(), listOpenParen, contentStart);
    }

    private boolean isAlreadyRewrittenClause(String sql, int listOpenParen, int contentStart) {
        if (!regionMatchesIgnoreCase(sql, contentStart, "select")) {
            return false;
        }
        int clauseEnd = findMatchingClosingParen(sql, listOpenParen);
        if (clauseEnd < 0) {
            return false;
        }
        String clause = sql.substring(contentStart, clauseEnd).toLowerCase();
        return clause.contains("table(") && clause.contains(config.oracleTableFunction().toLowerCase());
    }

    private static boolean isPlaceholderListStart(String sql, int contentStart) {
        return sql.charAt(contentStart) == '?';
    }

    private static InClauseMatch parsePlaceholderList(String sql, int keywordStart, int listOpenParen, int contentStart) {
        int position = contentStart;
        int paramCount = 0;

        while (position < sql.length()) {
            position = skipWhitespace(sql, position);
            if (position >= sql.length()) {
                return null;
            }

            if (sql.charAt(position) == ')') {
                return new InClauseMatch(keywordStart, listOpenParen, position + 1, paramCount, true);
            }

            if (sql.charAt(position) != '?') {
                int clauseEnd = findMatchingClosingParen(sql, listOpenParen);
                return clauseEnd < 0 ? null : new InClauseMatch(keywordStart, listOpenParen, clauseEnd, 0, false);
            }

            paramCount++;
            position++;

            position = skipWhitespace(sql, position);
            if (position < sql.length() && sql.charAt(position) == ',') {
                position++;
            } else {
                position = skipWhitespace(sql, position);
                if (position < sql.length() && sql.charAt(position) == ')') {
                    return new InClauseMatch(keywordStart, listOpenParen, position + 1, paramCount, true);
                }

                int clauseEnd = findMatchingClosingParen(sql, listOpenParen);
                return clauseEnd < 0 ? null : new InClauseMatch(keywordStart, listOpenParen, clauseEnd, 0, false);
            }
        }

        return null;
    }

    private static int findMatchingClosingParen(String sql, int openParenIndex) {
        int depth = 0;
        for (int index = openParenIndex; index < sql.length(); index++) {
            char character = sql.charAt(index);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
        }
        return -1;
    }

    private static int skipWhitespace(String sql, int from) {
        int position = from;
        while (position < sql.length() && Character.isWhitespace(sql.charAt(position))) {
            position++;
        }
        return position;
    }

    private static boolean isIdentifierChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static boolean hasKeywordBoundaryBefore(String sql, int index) {
        return index != 0 && isIdentifierChar(sql.charAt(index - 1));
    }

    private static boolean hasKeywordBoundaryAfter(String sql, int index) {
        return index < sql.length() && isIdentifierChar(sql.charAt(index));
    }

    private static boolean regionMatchesIgnoreCase(String sql, int offset, String keyword) {
        if (offset < 0 || offset + keyword.length() > sql.length()) {
            return false;
        }
        return sql.regionMatches(true, offset, keyword, 0, keyword.length());
    }

    private static String extractColumnExpression(String sql, int keywordStart) {
        int end = keywordStart;
        while (end > 0 && isSkippableBeforeInKeyword(sql.charAt(end - 1))) {
            end--;
        }

        int start = end;
        while (start > 0 && isColumnExpressionChar(sql.charAt(start - 1))) {
            start--;
        }

        return start == end ? "" : sql.substring(start, end);
    }

    private static boolean isSkippableBeforeInKeyword(char character) {
        return character == ',' || character == '(' || Character.isWhitespace(character);
    }

    private static boolean isColumnExpressionChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '.';
    }

    private static String generateQuestionMarks(int total) {
        StringBuilder queryPlaceholder = new StringBuilder(Math.max(0, total * 2 - 1));
        for (int i = 0; i < total; i++) {
            queryPlaceholder.append('?');
            if (i < total - 1) {
                queryPlaceholder.append(',');
            }
        }
        return queryPlaceholder.toString();
    }

    private record InKeywordMatch(int start, int end) {
    }

    private record InClauseMatch(int keywordStart, int listOpenParen, int end, int paramCount,
                                 boolean placeholderList) {
    }
}
