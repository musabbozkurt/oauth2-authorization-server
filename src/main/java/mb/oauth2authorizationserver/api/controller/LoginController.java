package mb.oauth2authorizationserver.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.service.SecurityService;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final SecurityService securityService;

    @GetMapping
    public String home() {
        return "index";
    }

    @GetMapping("/ott/sent")
    public String ottSent() {
        return "ott/sent";
    }

    @GetMapping("/success")
    public String success() {
        return "ott/success";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "index";
    }

    @GetMapping("/ott/submit")
    public String submit(Model model, @RequestParam("token") String token) {
        log.info("Received request to submit token: {}", token);
        model.addAttribute("token", token);
        return "ott/submit";
    }

    @GetMapping("/login")
    public String login(final Model model) {
        model.addAttribute("authentication", securityService.getLoggedInUserInfo());
        return "login";
    }

    @GetMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        securityService.logout();

        new SecurityContextLogoutHandler().logout(request, response, null);
        try {
            response.sendRedirect(request.getHeader("referer"));
        } catch (IOException e) {
            log.error("Exception occurred while logging out. logout - Exception: {}", ExceptionUtils.getStackTrace(e));
        }
    }
}
