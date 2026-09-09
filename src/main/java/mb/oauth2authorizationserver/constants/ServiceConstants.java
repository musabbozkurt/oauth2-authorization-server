package mb.oauth2authorizationserver.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceConstants {

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String ERROR = "error";
    public static final String MESSAGE = "message";
    public static final String PATH = "path";
    public static final String TIMESTAMP = "timestamp";
    public static final String EXCEPTION = "exception";
    public static final String SUCCESS = "success";

    public static final String ORGANIZATION = "organization";
    public static final String USER_ID = "userId";
    public static final String USER_FULL_NAME = "userFullName";
    public static final String SCOPE = "scope";
    public static final String EXPIRES_IN = "expires_in";

    public static final String CLIENT_DEVICE_COOKIE_NAME = "cdid";
    public static final String DOMAIN = "example.com";
    public static final int MAX_AGE = 3600 * 24 * 365;
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS_VALUE = "sentry-trace, baggage";

    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER_STRING = "Authorization";

    public static final String USERNAME_WITH_UNDERSCORE = "user_name";
    public static final String CLIENT_ID_WITH_UNDERSCORE = "client_id";
    public static final String CLIENT = "client";
    public static final String USER = "user";
    public static final String ATTEMPTS = "attempts";
    public static final String CLIENTS = "clients";
    public static final String USERS = "users";
    public static final String ADD_USER = "add-user";

    public static final String ACCESS_TOKEN_COOKIE_NAME = "st";
    public static final String CUSTOM_PASSWORD = "custom_password";

    public static final String CLIENT_SAVED = "Client saved";
    public static final String CLIENT_UPDATED = "Client updated";
    public static final String USER_SAVED = "User saved";
    public static final String USER_UPDATED = "User updated";

    public static final String AUTHORIZATION_LOCK = "oauth2-authorization-server:authorizationLock:%s:%s:%s";

    public static final String SESSION_REDIS_NAMESPACE = "spring:session";
    public static final String PRINCIPAL_INDEX_KEY_PREFIX = "%s:index:%s:".formatted(SESSION_REDIS_NAMESPACE, FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
    public static final String PRINCIPAL_INDEX_KEY_PATTERN = PRINCIPAL_INDEX_KEY_PREFIX + "*";
    public static final String SESSION_KEY_PREFIX = SESSION_REDIS_NAMESPACE + ":sessions:";
    public static final String SESSION_EXPIRES_KEY_PREFIX = SESSION_KEY_PREFIX + "expires:";

    public static String principalIndexKey(String principalName) {
        return PRINCIPAL_INDEX_KEY_PREFIX + principalName;
    }

    public static String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    public static String sessionExpiresKey(String sessionId) {
        return SESSION_EXPIRES_KEY_PREFIX + sessionId;
    }
}
