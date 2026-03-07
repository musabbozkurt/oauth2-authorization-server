package mb.oauth2authorizationserver.config.jpa;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Merged physical naming strategy that handles both:
 * <ul>
 *   <li>Dollar-sign schema placeholder replacement (e.g. {@code my_schema$} → {@code my_schema_dev})
 *       when used as a Spring-managed {@code @Primary} bean.</li>
 *   <li>Namespace-suffix appending based on the active profile, used when instantiated
 *       programmatically via {@link CustomCamelCaseToUnderscoresNamingStrategyFactory}.</li>
 * </ul>
 */
@Primary
@Component
@NoArgsConstructor
@AllArgsConstructor
public class CustomPhysicalNamingStrategy extends PhysicalNamingStrategySnakeCaseImpl {

    private static final Set<String> EXCLUDED_PROFILES = Set.of("stage", "prod");

    /**
     * Injected when used as a Spring bean; ignored when instantiated directly by the factory.
     */
    @Value("${environment-namespace:}")
    private String environmentNamespace;

    /**
     * Namespace suffix appended in non-production profiles (factory-driven usage).
     */
    private String namespace;

    /**
     * Active Spring profile used to decide whether the suffix is applied.
     */
    private String activeProfile;

    /**
     * Whether namespace-based schema naming is enabled (factory-driven usage).
     */
    private boolean namespaceEnabled;

    // -------------------------------------------------------------------------
    // Spring-bean constructor (only environmentNamespace is available via @Value)
    // -------------------------------------------------------------------------

    @Override
    public Identifier toPhysicalSchemaName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        if (identifier == null) {
            return super.toPhysicalSchemaName(null, jdbcEnvironment);
        }

        String name = identifier.getText();

        // Dollar-sign placeholder replacement (Spring-bean mode)
        if (name.endsWith("$") && environmentNamespace != null && !environmentNamespace.isEmpty()) {
            String schema = name.replace("$", environmentNamespace);
            return super.toPhysicalSchemaName(new Identifier(schema, identifier.isQuoted()), jdbcEnvironment);
        }

        // Namespace-suffix mode (factory-driven)
        if (namespaceEnabled && !EXCLUDED_PROFILES.contains(activeProfile)) {
            return super.toPhysicalSchemaName(
                    new Identifier(appendNamespace(name), false),
                    jdbcEnvironment
            );
        }

        return super.toPhysicalSchemaName(identifier, jdbcEnvironment);
    }

    @Override
    public Identifier toPhysicalCatalogName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        if (identifier != null) {
            String name = identifier.getText();
            if (name.endsWith("$") && environmentNamespace != null && !environmentNamespace.isEmpty()) {
                String schema = name.replace("$", environmentNamespace);
                return super.toPhysicalSchemaName(new Identifier(schema, identifier.isQuoted()), jdbcEnvironment);
            }
        }
        return super.toPhysicalCatalogName(identifier, jdbcEnvironment);
    }

    /**
     * Resolves the effective schema name by applying the namespace suffix rule.
     * <p>
     * In production and stage profiles (i.e. {@code prod} or {@code stage}), the provided
     * base schema is returned unchanged. In all other profiles (e.g. {@code dev}, {@code test}),
     * the namespace is appended as a suffix separated by an underscore
     * (e.g. {@code my_schema} becomes {@code my_schema_dev}).
     *
     * @param schema the base schema name to resolve (e.g. {@code my_schema})
     * @return the resolved schema name, with or without the namespace suffix
     */
    public String resolveSchema(String schema) {
        return EXCLUDED_PROFILES.contains(activeProfile) ? schema : appendNamespace(schema);
    }

    private String appendNamespace(String schema) {
        return "%s_%s".formatted(schema, namespace);
    }
}
