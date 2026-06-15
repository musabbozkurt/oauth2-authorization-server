package mb.oauth2authorizationserver.config;

import org.jspecify.annotations.NonNull;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CustomKeyGenerator implements KeyGenerator {

    @Override
    @SuppressWarnings("unchecked")
    public @NonNull Object generate(@NonNull Object target, @NonNull Method method, Object... params) {
        return "%s::%s".formatted(params[0], UUID.nameUUIDFromBytes(((Set<String>) params[1]).stream().sorted().collect(Collectors.joining(",")).getBytes()));
    }
}
