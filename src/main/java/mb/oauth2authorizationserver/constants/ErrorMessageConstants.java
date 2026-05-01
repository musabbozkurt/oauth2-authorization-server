package mb.oauth2authorizationserver.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessageConstants {

    public static final String USER_DISABLED = "Your account has been disabled.";
    public static final String MULTIPLE_USERS_WITH_SAME_EMAIL = "Multiple users found with the same email.";
    public static final String DUPLICATE_RECORD = "A record with these details already exists.";
    public static final String USERNAME_CAN_NOT_BE_EMPTY = "Username cannot be empty.";
    public static final String USERNAME_ALREADY_EXISTS = "This username is already in use: ";
    public static final String CLIENT_ID_ALREADY_EXISTS = "This Client Id is already in use: ";
    public static final String CLIENT_SECRET_LENGTH_INVALID = "Client Secret must be %d characters.";
    public static final String SESSION_EVICTED = "Session evicted";
    public static final String SESSION_NOT_FOUND = "Session not found";

    public static final String USER_NOT_FOUND = "user-not-found";
    public static final String CREDENTIALS_CAN_NOT_BE_EMPTY = "credentials-can-not-be-empty";
    public static final String PASSWORD_MISMATCH = "password-mismatch";

    public static String getErrorMessage(String actualMessage, String expectedMessage) {
        return expectedMessage.equals(actualMessage) ? expectedMessage : actualMessage;
    }
}
