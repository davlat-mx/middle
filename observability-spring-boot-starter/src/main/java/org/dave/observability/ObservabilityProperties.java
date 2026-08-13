package org.dave.observability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "middle.observability")
public class ObservabilityProperties {

    private String headerName = "X-Correlation-Id";

    private final Logging logging = new Logging();
    private final Idempotency idempotency = new Idempotency();

    @Data
    public static class Logging {
        private List<String> ignoredPaths = List.of("/actuator/**");
    }

    @Data
    public static class Idempotency {
        private boolean enabled = true;
    }
}
