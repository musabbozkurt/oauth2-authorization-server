package mb.oauth2authorizationserver.base;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.utility.TestcontainersConfiguration;

public class GlobalTestcontainersExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(@NonNull ExtensionContext context) {
        // Ensure testcontainers reuse is enabled in ~/.testcontainers.properties
        // This creates the file if it doesn't exist yet
        TestcontainersConfiguration.getInstance().updateUserConfig("testcontainers.reuse.enable", "true");
    }
}
