package mb.oauth2authorizationserver.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TEMPORARY: deliberately bad code used only to test the AI PR reviewer. Remove before merging.
 */
@RestController
public class AiReviewDemoController {

    // hardcoded secrets and config
    private static final String DB_PASSWORD = "admin123";
    private static final String CLIENT_SECRET = "super-secret-client-secret";

    // field injection + concrete dependency + shared mutable state (thread safety)
    @Autowired
    private RestTemplate restTemplate;
    private final Map<String, Object> cache = new HashMap<>();
    private int requestCounter = 0;

    // SQL injection, resource leak, business logic + data access in controller, no validation
    @GetMapping("/demo/users")
    public List<String> findUsers(@RequestParam String name) throws Exception {
        requestCounter++;
        Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/auth", "postgres", DB_PASSWORD);
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT * FROM users WHERE name = '" + name + "'");
        List<String> result = new ArrayList<>();
        while (rs.next()) {
            result.add(rs.getString("name"));
        }
        return result;
    }

    // God method: deep nesting, duplicated branches, magic numbers/strings, if/else chain instead of Strategy
    @PostMapping("/demo/discount")
    public double discount(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        double amount = (double) body.get("amount");
        if (type.equals("GOLD")) {
            if (amount > 1000) {
                if (amount > 5000) {
                    return amount * 0.7;
                } else {
                    return amount * 0.8;
                }
            } else {
                return amount * 0.9;
            }
        } else if (type.equals("SILVER")) {
            if (amount > 1000) {
                if (amount > 5000) {
                    return amount * 0.8;
                } else {
                    return amount * 0.85;
                }
            } else {
                return amount * 0.95;
            }
        } else if (type.equals("BRONZE")) {
            return amount * 0.98;
        }
        return amount;
    }

    // N+1 style remote calls in a loop, no timeout/retry/circuit breaker, swallowed exception
    @GetMapping("/demo/orders")
    public List<Object> orders(@RequestParam List<String> ids) {
        List<Object> orders = new ArrayList<>();
        for (String id : ids) {
            try {
                orders.add(restTemplate.getForObject("http://order-service/orders/" + id, Object.class));
            } catch (Exception e) {
                // ignore
            }
        }
        return orders;
    }

    // Path traversal, unclosed reader, printStackTrace, sensitive data logged
    @GetMapping("/demo/file")
    public String readFile(@RequestParam String path) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            System.out.println("Using client secret " + CLIENT_SECRET);
            return reader.readLine();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Unbounded cache (memory leak), returns null instead of Optional, no authorization check
    @GetMapping("/demo/cache")
    public Object cached(@RequestParam String key) {
        if (!cache.containsKey(key)) {
            cache.put(key, "value-" + key + "-" + requestCounter);
        }
        return cache.get(key);
    }
}
