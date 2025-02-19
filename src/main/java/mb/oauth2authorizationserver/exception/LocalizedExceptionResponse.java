package mb.oauth2authorizationserver.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Objects;

import static java.util.Collections.EMPTY_LIST;

@Getter
@NoArgsConstructor
public class LocalizedExceptionResponse extends ErrorResponse {

    private static final String PREFIX = "error.%s";
    private static final String DEFAULT = "DEFAULT";

    @Setter(AccessLevel.PACKAGE)
    private static MessageSourceAccessor messages;

    public LocalizedExceptionResponse(String errorCode) {
        super(errorCode, getMessage(errorCode, EMPTY_LIST));
    }

    public LocalizedExceptionResponse(String errorCode, Collection<?> args) {
        super(errorCode, getMessage(errorCode, args), args);
    }

    public LocalizedExceptionResponse(String errorCode, String message, Collection<?> args) {
        super(errorCode, StringUtils.isNotBlank(message) ? message : getMessage(errorCode, args), args);
    }

    public LocalizedExceptionResponse(String errorCode, String message) {
        super(errorCode, StringUtils.isNotBlank(message) ? message : getMessage(errorCode, EMPTY_LIST), EMPTY_LIST);
    }

    private static String getMessage(String errorCode, Collection<?> args) {
        try {
            if (Objects.isNull(messages)) {
                messages = new MessageSourceAccessor(messageSource());
            }

            if (CollectionUtils.isEmpty(args)) {
                return messages.getMessage(String.format(PREFIX, errorCode));
            }

            Object[] argsArray = args
                    .stream()
                    .map(arg -> arg instanceof Number ? String.valueOf(arg) : arg)
                    .toArray();

            return messages.getMessage(String.format(PREFIX, errorCode), argsArray);
        } catch (Exception ex) {
            return messages.getMessage(String.format(PREFIX, DEFAULT));
        }
    }

    private static MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:messages", "classpath:messages-default");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }
}
