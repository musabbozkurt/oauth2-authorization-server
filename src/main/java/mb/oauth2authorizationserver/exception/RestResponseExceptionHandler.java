package mb.oauth2authorizationserver.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@ControllerAdvice
public class RestResponseExceptionHandler {

    @ResponseBody
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
        log.error("BaseException occurred: {}", ex.getErrorCode(), ex);
        return new ResponseEntity<>(new LocalizedExceptionResponse(ex.getErrorCode().getCode(), ex.getMessage(), ex.getParams()), ex.getErrorCode().getHttpStatus());
    }

    @ResponseBody
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Exception occurred: {}", ExceptionUtils.getStackTrace(ex));
        if (ex instanceof BadCredentialsException) {
            return new ResponseEntity<>(new LocalizedExceptionResponse(OAuth2AuthorizationServerServiceErrorCode.BAD_CREDENTIALS.getCode(), ex.getMessage()), OAuth2AuthorizationServerServiceErrorCode.BAD_CREDENTIALS.getHttpStatus());
        }
        return new ResponseEntity<>(new LocalizedExceptionResponse(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR.getCode(), ex.getMessage()), OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR.getHttpStatus());
    }
}
