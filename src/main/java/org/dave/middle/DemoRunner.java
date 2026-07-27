package org.dave.middle;

import org.dave.middle.domain.model.Country;
import org.dave.middle.domain.model.Currency;
import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.rule.ValidationEngine;
import org.dave.middle.domain.vo.Corridor;
import org.dave.middle.domain.vo.Money;
import org.dave.middle.repository.TransferRepository;
import org.dave.middle.service.ReportService;
import org.dave.middle.service.TransferService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DemoRunner implements CommandLineRunner {

    @Override
    public void run(String... args) {
        TransferRepository repository = new TransferRepository();
        TransferService transferService = new TransferService(ValidationEngine.withDefaults(), repository);
        ReportService reportService = new ReportService(repository);

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
    }
}
