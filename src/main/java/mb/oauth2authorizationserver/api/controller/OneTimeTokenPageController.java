package mb.oauth2authorizationserver.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OneTimeTokenPageController {

    @GetMapping
    public String home() {
        return "index";
    }

    @GetMapping("/ott/sent")
    public String ottSent() {
        return "sent";
    }
}
