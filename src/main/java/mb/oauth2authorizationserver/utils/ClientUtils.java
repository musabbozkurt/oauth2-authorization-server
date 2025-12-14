package mb.oauth2authorizationserver.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import mb.oauth2authorizationserver.constants.ServiceConstants;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientUtils {

    public static String getDeviceId(HttpServletRequest request) {
        if (request != null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (c.getName().equals(ServiceConstants.CLIENT_DEVICE_COOKIE_NAME)) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
