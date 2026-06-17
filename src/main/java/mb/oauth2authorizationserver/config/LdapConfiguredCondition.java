package mb.oauth2authorizationserver.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class LdapConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        return Binder.get(context.getEnvironment())
                .bind("spring.ldap", CustomLdapProperties.class)
                .map(CustomLdapProperties::isValid)
                .orElseGet(() -> false);
    }
}
