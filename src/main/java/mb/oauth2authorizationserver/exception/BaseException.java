package mb.oauth2authorizationserver.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.Collection;

import static java.util.Collections.EMPTY_LIST;

@Data
@ToString
@EqualsAndHashCode(callSuper = true)
public class BaseException extends RuntimeException implements Serializable {

    private final transient ErrorCode errorCode;
    private final String message;
    private final transient Collection<?> params;

    public BaseException() {
        this(null, null, EMPTY_LIST);
    }

    public BaseException(ErrorCode errorCode) {
        this(errorCode, null, EMPTY_LIST);
    }

    public BaseException(ErrorCode errorCode, Collection<?> params) {
        this(errorCode, null, params);
    }

    public BaseException(ErrorCode errorCode, String message, Collection<?> params) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.params = params;
    }
}
