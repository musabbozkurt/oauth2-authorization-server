package mb.oauth2authorizationserver.config.security.service.impl;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OneTimeTokenSuccessHandlerImpl implements OneTimeTokenGenerationSuccessHandler {

    private final FlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    @SneakyThrows
    public void handle(HttpServletRequest request, HttpServletResponse response, OneTimeToken oneTimeToken) throws IOException, ServletException {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
                .replacePath(request.getContextPath())
                .replaceQuery(null)
                .fragment(null)
                .path("/ott/submit")
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

        RedirectView redirectView = new RedirectView("/ott/sent");
        redirectView.setExposeModelAttributes(false);
        FlashMap flashMap = new FlashMap();
        flashMap.put("token", oneTimeToken.getTokenValue());
        flashMap.put("ottSubmitUrl", ottLink);

        flashMapManager.saveOutputFlashMap(flashMap, request, response);
        redirectView.render(flashMap, request, response);
    }
}
