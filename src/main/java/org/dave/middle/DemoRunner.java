package org.dave.middle;

import org.dave.middle.domain.model.Country;
import org.dave.middle.domain.model.Currency;
import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.rule.ValidationEngine;
import org.dave.middle.domain.vo.Corridor;
import org.dave.middle.domain.vo.Money;
import org.dave.middle.queue.ProcessorStats;
import org.dave.middle.queue.RetryPolicy;
import org.dave.middle.queue.TransferExecutor;
import org.dave.middle.queue.TransferQueueProcessor;
import org.dave.middle.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.dave.middle.service.ReportService;
import org.dave.middle.service.TransferService;
import org.dave.observability.IdempotencyGuard;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class DemoRunner implements CommandLineRunner {

    private final TransferRepository repository;
    private final TransferService transferService;
    private final ReportService reportService;
    private final IdempotencyGuard idempotencyGuard; // из стартера (авто-конфиг)
    private final ValidationEngine validationEngine = ValidationEngine.withDefaults(); // для очереди M2; не бин

    @Override
    public void run(String... args) throws InterruptedException {
        List<Transfer> incoming = List.of(
                Transfer.create("t-1", "client-1", "client-2",
                        Money.of("1500000", Currency.UZS), Corridor.of(Country.UZ, Country.RU)),
                Transfer.create("t-2", "client-3", "client-4",
                        Money.of("2500.50", Currency.RUB), Corridor.of(Country.RU, Country.UZ)),
                Transfer.create("t-3", "client-5", "client-6",
                        Money.of("300", Currency.USD), Corridor.of(Country.UZ, Country.KZ)),
                // плохая валюта для коридора UZ->KZ (разрешён только USD)
                Transfer.create("t-4", "client-7", "client-8",
                        Money.of("999.99", Currency.RUB), Corridor.of(Country.UZ, Country.KZ)),
                // перевод самому себе
                Transfer.create("t-5", "client-9", "client-9",
                        Money.of("100", Currency.USD), Corridor.of(Country.KZ, Country.RU))
        );

        System.out.println("=== Обработка заявок ===");
        incoming.forEach(transfer -> {
            TransferService.SubmitResult result = transferService.submit(transfer);
            System.out.printf("%s -> %s%s%n",
                    transfer.getId(),
                    transfer.getStatus(),
                    result.accepted() ? "" : " " + result.errors());
        });

        System.out.println();
        System.out.println("=== Отчёт 1: количество по статусам ===");
        reportService.countByStatus()
                .forEach((status, count) -> System.out.printf("  %-8s %d%n", status, count));

        System.out.println();
        System.out.println("=== Отчёт 2: оборот по коридорам ===");
        reportService.turnoverByCorridor()
                .forEach((corridor, total) -> System.out.printf("  %-8s %s%n", corridor, total.toPlainString()));

        System.out.println();
        System.out.println("=== Отчёт 3: принятые / отклонённые ===");
        reportService.successVsFailed().forEach((accepted, transfers) ->
                System.out.printf("  %s: %s%n",
                        accepted ? "принятые " : "отклонённые",
                        transfers.stream().map(Transfer::getId).sorted().toList()));

        runQueueDemo();
    }

    // M2: фоновая обработка очереди на virtual threads
    private void runQueueDemo() throws InterruptedException {
        System.out.println();
        System.out.println("=== M2: фоновая обработка очереди (virtual threads) ===");

        Map<String, Integer> attempts = new ConcurrentHashMap<>();
        TransferExecutor flaky = transfer -> {
            int n = attempts.merge(transfer.getId(), 1, Integer::sum);
            if (transfer.getId().equals("q-2") && n < 3) {
                throw new IllegalStateException("временный сбой сети, попытка " + n);
            }
        };

        List<Transfer> jobs = List.of(
                Transfer.create("q-1", "c1", "c2",
                        Money.of("100", Currency.USD), Corridor.of(Country.UZ, Country.KZ)),
                Transfer.create("q-2", "c3", "c4",
                        Money.of("200", Currency.USD), Corridor.of(Country.UZ, Country.KZ)),
                Transfer.create("q-3", "c5", "c5",
                        Money.of("300", Currency.USD), Corridor.of(Country.UZ, Country.KZ)));

        try (TransferQueueProcessor processor = new TransferQueueProcessor(
                validationEngine, repository, flaky, RetryPolicy.withDefaults(), idempotencyGuard)) {
            processor.start();
            jobs.forEach(processor::enqueue);
            processor.enqueue(jobs.get(0));
            processor.awaitEmpty(Duration.ofSeconds(5));

            repository.findAll().stream()
                    .filter(t -> t.getId().startsWith("q-"))
                    .sorted(Comparator.comparing(Transfer::getId))
                    .forEach(t -> System.out.printf("  %s -> %s%n", t.getId(), t.getStatus()));

            ProcessorStats.Snapshot snap = processor.stats().snapshot();
            System.out.printf("  итог: успешно=%d, отклонено=%d, повторов=%d, дубликатов=%d%n",
                    snap.succeeded(), snap.failed(), snap.retries(), snap.duplicates());
        }
    }
}
