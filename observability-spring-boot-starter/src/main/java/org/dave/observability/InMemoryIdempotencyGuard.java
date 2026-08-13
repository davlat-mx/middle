package org.dave.observability;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyGuard implements IdempotencyGuard {

    private final Set<String> claimed = ConcurrentHashMap.newKeySet();

    @Override
    public boolean claim(String key) {
        return claimed.add(key);
    }
}
