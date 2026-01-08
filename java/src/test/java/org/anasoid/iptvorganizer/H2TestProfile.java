package org.anasoid.iptvorganizer;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class H2TestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        // Tests use MySQL configuration with DevServices disabled
        // For CI/local testing, either provide MySQL or use Docker with TestContainers
        return Map.of(
            "quarkus.datasource.db-kind", "mysql",
            "quarkus.datasource.devservices.enabled", "false",
            "quarkus.scheduler.enabled", "false"
        );
    }
}
