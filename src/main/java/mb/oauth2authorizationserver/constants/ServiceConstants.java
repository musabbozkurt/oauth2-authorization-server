package mb.oauth2authorizationserver.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceConstants {

    public static final String USERNAME = "username";
    public static final String ERROR = "error";
    public static final String MESSAGE = "message";
    public static final String PATH = "path";
    public static final String TIMESTAMP = "timestamp";
    public static final String EXCEPTION = "exception";
    public static final String SUCCESS = "success";
    public static final String CLIENT_DEVICE_COOKIE_NAME = "cdid";
    public static final String DOMAIN = "domain.com";
    public static final int MAX_AGE = 3600 * 24 * 365;
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS_VALUE = "sentry-trace, baggage";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER_STRING = "Authorization";
    public static final String USERNAME_WITH_UNDERSCORE = "user_name";
    public static final String ACCESS_TOKEN_COOKIE_NAME = "st";
}
