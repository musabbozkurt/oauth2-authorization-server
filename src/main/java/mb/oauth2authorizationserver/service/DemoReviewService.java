package mb.oauth2authorizationserver.service;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.model.request.DiscountRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for the demo endpoints; the controller only handles HTTP concerns.
 */
@Slf4j
@Service
public class DemoReviewService {

    private static final int MAX_CACHE_ENTRIES = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final String orderServiceUrl;
    private final Path filesBaseDir;

    // Bounded LRU cache; synchronizedMap makes computeIfAbsent thread-safe
    private final Map<String, String> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    public DemoReviewService(JdbcTemplate jdbcTemplate,
                             @Qualifier("demoRestClient") RestClient restClient,
                             @Value("${app.demo.order-service-url:http://order-service/orders}") String orderServiceUrl,
                             @Value("${app.demo.files-dir:/tmp/demo-files}") String filesDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.restClient = restClient;
        this.orderServiceUrl = orderServiceUrl;
        this.filesBaseDir = Path.of(filesDir).toAbsolutePath().normalize();
    }

    public List<String> findUsers(String name) {
        return jdbcTemplate.queryForList("SELECT name FROM users WHERE name = ?", String.class, name);
    }

    public BigDecimal discount(DiscountRequest request) {
        return request.type().apply(request.amount());
    }

    public List<Object> findOrders(List<String> ids) {
        List<Object> orders = new ArrayList<>();
        for (String id : ids) {
            try {
                orders.add(restClient.get().uri(orderServiceUrl + "/{id}", id).retrieve().body(Object.class));
            } catch (RestClientException e) {
                log.warn("Order lookup failed, skipping the order", e);
            }
        }
        return orders;
    }

    public String readFirstLine(String relativePath) {
        Path target = filesBaseDir.resolve(relativePath).normalize();
        if (!target.startsWith(filesBaseDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }
        try (BufferedReader reader = Files.newBufferedReader(target)) {
            return reader.readLine();
        } catch (NoSuchFileException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found", e);
        } catch (IOException e) {
            log.error("Could not read file", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file", e);
        }
    }

    public String cached(String key) {
        return cache.computeIfAbsent(key, k -> "value-" + k);
    }
}
