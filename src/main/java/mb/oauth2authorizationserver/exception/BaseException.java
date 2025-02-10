package mb.oauth2authorizationserver.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.util.Collection;

import static java.util.Collections.EMPTY_LIST;

@Data
@ToString
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BaseException extends RuntimeException implements Serializable {

    private ErrorCode errorCode;
    private String message;
    private Collection<?> params;

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
