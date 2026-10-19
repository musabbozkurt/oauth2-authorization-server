package mb.oauth2authorizationserver.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.model.request.DiscountRequest;
import mb.oauth2authorizationserver.service.DemoReviewService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Thin controller: validates input and delegates to {@link DemoReviewService}.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class AIReviewDemoController {

    private final DemoReviewService demoReviewService;

    @GetMapping("/demo/users")
    public List<String> findUsers(@RequestParam @NotBlank @Size(max = 100) String name) {
        return demoReviewService.findUsers(name);
    }

    @PostMapping("/demo/discount")
    public BigDecimal discount(@Valid @RequestBody DiscountRequest request) {
        return demoReviewService.discount(request);
    }

    @GetMapping("/demo/orders")
    public List<Object> orders(@RequestParam @Size(max = 50) List<@NotBlank String> ids) {
        return demoReviewService.findOrders(ids);
    }

    @GetMapping("/demo/file")
    public String readFile(@RequestParam @NotBlank String path) {
        return demoReviewService.readFirstLine(path);
    }

    @GetMapping("/demo/cache")
    public String cached(@RequestParam @NotBlank @Size(max = 100) String key) {
        return demoReviewService.cached(key);
    }
}
