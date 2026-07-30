package org.dave.middle.queue;

import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.rule.ValidationEngine;
import org.dave.middle.repository.TransferRepository;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class TransferQueueProcessor implements AutoCloseable {

    private final ValidationEngine validationEngine;
    private final TransferRepository repository;
    private final TransferExecutor executor;
    private final RetryPolicy retryPolicy;
    private final ProcessorStats stats;

    private final BlockingQueue<Transfer> queue = new LinkedBlockingQueue<>();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    // идемпотентность: id, которые уже взяли в работу
    private final Set<String> claimed = ConcurrentHashMap.newKeySet();

    // сколько заявок ещё не доработано — для awaitEmpty
    private final AtomicLong pending = new AtomicLong();
    private final Object idleLock = new Object();

    private volatile boolean running;
    private Thread dispatcher;

    public TransferQueueProcessor(ValidationEngine validationEngine,
                                  TransferRepository repository,
                                  TransferExecutor executor,
                                  RetryPolicy retryPolicy) {
        this.validationEngine = validationEngine;
        this.repository = repository;
        this.executor = executor;
        this.retryPolicy = retryPolicy;
        this.stats = new ProcessorStats();
    }

    public void start() {
        running = true;
        dispatcher = Thread.ofVirtual().name("queue-dispatcher").start(this::dispatchLoop);
    }

    public void enqueue(Transfer transfer) {
        pending.incrementAndGet();
        try {
            queue.put(transfer);
        } catch (InterruptedException e) {
            pending.decrementAndGet();
            Thread.currentThread().interrupt();
            throw new RuntimeException("прервано при постановке в очередь", e);
        }
    }

    public ProcessorStats stats() {
        return stats;
    }

    private void dispatchLoop() {
        while (running) {
            try {
                Transfer transfer = queue.take();
                workers.submit(() -> handle(transfer));
            } catch (InterruptedException e) {
                break; // остановка через close()
            }
        }
    }

    private void handle(Transfer transfer) {
        try {
            if (!claimed.add(transfer.getId())) {
                stats.duplicate();
                return;
            }
            process(transfer);
        } finally {
            finish();
        }
    }

    private void process(Transfer transfer) {
        List<String> errors = validationEngine.validate(transfer);
        if (!errors.isEmpty()) {
            transfer.fail();
            repository.save(transfer);
            stats.failed();
            return;
        }

        transfer.perform();
        repository.save(transfer);

        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                executor.execute(transfer);
                transfer.success();
                repository.save(transfer);
                stats.succeeded();
                return;
            } catch (RuntimeException e) {
                if (attempt == retryPolicy.maxAttempts()) {
                    transfer.fail();
                    repository.save(transfer);
                    stats.failed();
                    return;
                }
                stats.retry();
                sleep(retryPolicy.backoffFor(attempt));
            }
        }
    }

    public boolean awaitEmpty(Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        synchronized (idleLock) {
            while (pending.get() > 0) {
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0) {
                    return false;
                }
                idleLock.wait(wait);
            }
        }
        return true;
    }

    private void finish() {
        if (pending.decrementAndGet() == 0) {
            synchronized (idleLock) {
                idleLock.notifyAll();
            }
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("прервано во время backoff", e);
        }
    }

    @Override
    public void close() {
        running = false;
        if (dispatcher != null) {
            dispatcher.interrupt();
        }
        workers.close();
    }
}
