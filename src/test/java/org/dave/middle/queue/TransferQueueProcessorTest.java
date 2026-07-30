package org.dave.middle.queue;

import org.dave.middle.domain.model.Country;
import org.dave.middle.domain.model.Currency;
import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.model.TransferStatus;
import org.dave.middle.domain.rule.ValidationEngine;
import org.dave.middle.domain.vo.Corridor;
import org.dave.middle.domain.vo.Money;
import org.dave.middle.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferQueueProcessorTest {

    private static final RetryPolicy FAST = new RetryPolicy(3, Duration.ofMillis(1));
    private static final Duration WAIT = Duration.ofSeconds(5);

    private TransferRepository repository;

    @BeforeEach
    void setUp() {
        repository = new TransferRepository();
    }

    private static Transfer ok(String id) {
        return Transfer.create(id, "a", "b",
                Money.of("100", Currency.USD), Corridor.of(Country.UZ, Country.KZ));
    }

    private TransferQueueProcessor processor(TransferExecutor executor) {
        return new TransferQueueProcessor(ValidationEngine.withDefaults(), repository, executor, FAST);
    }

    @Test
    @DisplayName("валидная заявка доходит до SUCCESS")
    void validGoesToSuccess() throws InterruptedException {
        try (TransferQueueProcessor processor = processor(TransferExecutor.alwaysOk())) {
            processor.start();
            processor.enqueue(ok("t-1"));
            assertTrue(processor.awaitEmpty(WAIT));

            assertEquals(TransferStatus.SUCCESS, repository.findById("t-1").orElseThrow().getStatus());
            assertEquals(1, processor.stats().snapshot().succeeded());
        }
    }

    @Test
    @DisplayName("отказ бизнес-правил -> FAILED без повторов")
    void invalidGoesToFailed() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        try (TransferQueueProcessor processor = processor(t -> calls.incrementAndGet())) {
            processor.start();
            // отправитель == получатель
            processor.enqueue(Transfer.create("t-2", "a", "a",
                    Money.of("100", Currency.USD), Corridor.of(Country.UZ, Country.KZ)));
            assertTrue(processor.awaitEmpty(WAIT));

            assertEquals(TransferStatus.FAILED, repository.findById("t-2").orElseThrow().getStatus());
            assertEquals(1, processor.stats().snapshot().failed());
            assertEquals(0, processor.stats().snapshot().retries());
            assertEquals(0, calls.get()); // executor даже не звали
        }
    }

    @Test
    @DisplayName("идемпотентность: дубликат id обрабатывается один раз")
    void duplicateProcessedOnce() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        try (TransferQueueProcessor processor = processor(t -> calls.incrementAndGet())) {
            processor.start();
            Transfer transfer = ok("t-3");
            processor.enqueue(transfer);
            processor.enqueue(transfer); // тот же id
            assertTrue(processor.awaitEmpty(WAIT));

            assertEquals(1, calls.get());
            assertEquals(1, processor.stats().snapshot().succeeded());
            assertEquals(1, processor.stats().snapshot().duplicates());
        }
    }

    @Test
    @DisplayName("временный сбой -> повтор с backoff -> SUCCESS")
    void retriesThenSuccess() throws InterruptedException {
        // падает первые две попытки, третья проходит
        AtomicInteger calls = new AtomicInteger();
        TransferExecutor flaky = t -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("временный сбой");
            }
        };
        try (TransferQueueProcessor processor = processor(flaky)) {
            processor.start();
            processor.enqueue(ok("t-4"));
            assertTrue(processor.awaitEmpty(WAIT));

            assertEquals(TransferStatus.SUCCESS, repository.findById("t-4").orElseThrow().getStatus());
            assertEquals(3, calls.get());
            assertEquals(2, processor.stats().snapshot().retries());
            assertEquals(1, processor.stats().snapshot().succeeded());
        }
    }

    @Test
    @DisplayName("попытки исчерпаны -> FAILED")
    void retriesExhaustedFailed() throws InterruptedException {
        TransferExecutor broken = t -> {
            throw new IllegalStateException("сервис лежит");
        };
        try (TransferQueueProcessor processor = processor(broken)) {
            processor.start();
            processor.enqueue(ok("t-5"));
            assertTrue(processor.awaitEmpty(WAIT));

            assertEquals(TransferStatus.FAILED, repository.findById("t-5").orElseThrow().getStatus());
            assertEquals(1, processor.stats().snapshot().failed());
            assertEquals(2, processor.stats().snapshot().retries()); // maxAttempts - 1
        }
    }

    @Test
    @DisplayName("много заявок разом обрабатываются параллельно и все доходят")
    void concurrentLoad() throws InterruptedException {
        int n = 500;
        ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();
        try (TransferQueueProcessor processor = processor(t -> seen.put(t.getId(), true))) {
            processor.start();
            for (int i = 0; i < n; i++) {
                processor.enqueue(ok("t-" + i));
            }
            assertTrue(processor.awaitEmpty(WAIT));

            assertEquals(n, seen.size());
            assertEquals(n, repository.count());
            assertEquals(n, processor.stats().snapshot().succeeded());
        }
    }
}
