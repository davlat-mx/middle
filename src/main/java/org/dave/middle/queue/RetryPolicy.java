package org.dave.middle.queue;

import java.time.Duration;

public record RetryPolicy(int maxAttempts, Duration baseBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts должно быть >= 1, получено: " + maxAttempts);
        }
        if (baseBackoff == null || baseBackoff.isNegative()) {
            throw new IllegalArgumentException("baseBackoff не может быть отрицательным");
        }
    }

    public static RetryPolicy withDefaults() {
        return new RetryPolicy(3, Duration.ofMillis(50));
    }

    public Duration backoffFor(int attempt) {
        long millis = baseBackoff.toMillis() << (attempt - 1);
        return Duration.ofMillis(millis);
    }
}
