package mb.oauth2authorizationserver.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.ldap.LdapProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Setter
@Getter
@Primary
@Configuration
@ConfigurationProperties(prefix = "spring.ldap")
public class CustomLdapProperties extends LdapProperties {

    private String userDn;
    private String userSearchBase;
    private String userSearchFilter;

    public boolean isValid() {
        return ArrayUtils.isNotEmpty(getUrls())
                && StringUtils.isNotBlank(getPassword())
                && StringUtils.isNotBlank(userDn)
                && StringUtils.isNotBlank(userSearchBase)
                && StringUtils.isNotBlank(userSearchFilter);
    }
}
