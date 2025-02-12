package mb.oauth2authorizationserver.config.security.service.impl;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.authentication.ott.RedirectOneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OneTimeTokenSuccessHandlerImpl implements OneTimeTokenGenerationSuccessHandler {

    private final OneTimeTokenGenerationSuccessHandler redirectHandler = new RedirectOneTimeTokenGenerationSuccessHandler("/ott/sent");

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, OneTimeToken oneTimeToken) throws IOException, ServletException {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
                .replacePath(request.getContextPath())
                .replaceQuery(null)
                .fragment(null)
                .path("/login/ott")
                .queryParam("token", oneTimeToken.getTokenValue());

        String ottLink = builder.toUriString();
        var sendTo = oneTimeToken.getUsername();

        // send email (JavaMail, SendGrid) or SMS
        var body = """
                Hello %s, from Spring Security!
                Below you will find your one time token link to login to our super secret application!
                %s
                """.formatted(sendTo, ottLink);

        log.info("Sending One Time Token to username: {}, body: {}", sendTo, body);

        this.redirectHandler.handle(request, response, oneTimeToken);
    }
}
