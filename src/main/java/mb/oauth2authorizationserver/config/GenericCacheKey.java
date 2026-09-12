package mb.oauth2authorizationserver.config;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * A generic, immutable cache key composite.
 *
 * @param query   The search criteria/DTO object.
 * @param context Additional tracking fields, like projected fields or pagination.
 * @param <Q>     The type of the query DTO.
 * @param <C>     The type of the context/return fields set.
 */
public record GenericCacheKey<Q, C>(Q query, C context) {

    // Canonical constructor with null-safety checks
    public GenericCacheKey {
        Objects.requireNonNull(query, "Query payload cannot be null");
    }

    /**
     * Optional: Keeps your existing behavior if your caching layer (like Redis)
     * strictly requires a String instead of the Key object itself.
     */
    @Override
    public @NonNull String toString() {
        return String.valueOf(hashCode());
    }
}
