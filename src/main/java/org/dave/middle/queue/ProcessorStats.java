package org.dave.middle.queue;

import java.util.concurrent.atomic.AtomicLong;

public final class ProcessorStats {

    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();

    void succeeded() {
        succeeded.incrementAndGet();
    }

    void failed() {
        failed.incrementAndGet();
    }

    void retry() {
        retries.incrementAndGet();
    }

    void duplicate() {
        duplicates.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(succeeded.get(), failed.get(), retries.get(), duplicates.get());
    }

    public record Snapshot(long succeeded, long failed, long retries, long duplicates) {
    }
}
