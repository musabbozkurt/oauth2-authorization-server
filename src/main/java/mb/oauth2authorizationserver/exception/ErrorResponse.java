package mb.oauth2authorizationserver.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collection;

import static java.util.Collections.EMPTY_LIST;

@Data
@NoArgsConstructor
public class ErrorResponse implements Serializable {

    private String errorCode;
    private String message;

    @JsonUnwrapped
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Collection<?> params;

    @JsonCreator
    public ErrorResponse(@JsonProperty("errorCode") String errorCode, @JsonProperty("message") String message) {
        this(errorCode, message, EMPTY_LIST);
    }

    public ErrorResponse(String errorCode, String message, Collection<?> params) {
        this.errorCode = errorCode;
        this.message = message;
        this.params = params;
    }

    public ErrorResponse(ErrorCode errorCode, String message) {
        this.errorCode = errorCode.getCode();
        this.message = message;
    }
}
