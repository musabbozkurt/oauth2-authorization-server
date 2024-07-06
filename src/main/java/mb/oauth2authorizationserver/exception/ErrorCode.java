package mb.oauth2authorizationserver.exception;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.http.HttpStatus;

@JsonDeserialize(as = OAuth2AuthorizationServerServiceErrorCode.class)
public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getCode();
}
