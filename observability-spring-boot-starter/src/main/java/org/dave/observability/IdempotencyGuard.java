package org.dave.observability;

public interface IdempotencyGuard {

    boolean claim(String key);
}
