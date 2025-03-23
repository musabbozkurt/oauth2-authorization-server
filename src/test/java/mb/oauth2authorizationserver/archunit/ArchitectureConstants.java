package mb.oauth2authorizationserver.archunit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ArchitectureConstants {

    public static final String DEFAULT_PACKAGE = "mb.oauth2authorizationserver";

    public static final String CONTROLLER_SUFFIX = "Controller";
    public static final String SERVICE_SUFFIX = "Service";
    public static final String REPOSITORY_SUFFIX = "Repository";

    public static final String PACKAGE_CONTROLLER = "..controller..";
    public static final String PACKAGE_SERVICE = "..service..";
    public static final String PACKAGE_REPOSITORY = "..repository..";
    public static final String PACKAGE_SECURITY_BUILDER_IMPL = "..config.security.builder.impl..";
    public static final String PACKAGE_SECURITY_PROVIDER = "..config.security.provider..";
}
