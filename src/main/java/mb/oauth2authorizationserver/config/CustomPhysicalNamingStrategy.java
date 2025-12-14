package mb.oauth2authorizationserver.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CustomPhysicalNamingStrategy extends PhysicalNamingStrategySnakeCaseImpl {

    @Value("${environment-namespace}")
    private String environmentNamespace;

    @Override
    public Identifier toPhysicalSchemaName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        if (identifier != null) {
            String name = identifier.getText();
            if (name.endsWith("$")) {
                String schema = name.replace("$", environmentNamespace);
                return super.toPhysicalSchemaName(new Identifier(schema, identifier.isQuoted()), jdbcEnvironment);
            }
        }
        return super.toPhysicalSchemaName(identifier, jdbcEnvironment);
    }

    @Override
    public Identifier toPhysicalCatalogName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        if (identifier != null) {
            String name = identifier.getText();
            if (name.endsWith("$")) {
                String schema = name.replace("$", environmentNamespace);
                return super.toPhysicalSchemaName(new Identifier(schema, identifier.isQuoted()), jdbcEnvironment);
            }
        }
        return super.toPhysicalCatalogName(identifier, jdbcEnvironment);
    }
}
