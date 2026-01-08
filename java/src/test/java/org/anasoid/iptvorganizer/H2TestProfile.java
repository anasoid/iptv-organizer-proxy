package org.anasoid.iptvorganizer;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class H2TestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        // Use H2 in-memory database for testing
        return Map.of(
            "quarkus.datasource.db-kind", "h2",
            "quarkus.datasource.jdbc.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;MODE=MySQL",
            "quarkus.datasource.username", "sa",
            "quarkus.datasource.password", "",
            "quarkus.datasource.jdbc.enable-validation", "false",
            "quarkus.scheduler.enabled", "false"
        );
    }
}
