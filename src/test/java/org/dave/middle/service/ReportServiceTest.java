package org.dave.middle.service;

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

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ReportServiceTest {

    private static final Corridor UZ_RU = Corridor.of(Country.UZ, Country.RU);
    private static final Corridor UZ_KZ = Corridor.of(Country.UZ, Country.KZ);

    private TransferRepository repository;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        repository = new TransferRepository();
        TransferService transferService = new TransferService(ValidationEngine.withDefaults(), repository);
        reportService = new ReportService(repository);

        List.of(
                Transfer.create("t-1", "a", "b", Money.of("100", Currency.UZS), UZ_RU),
                Transfer.create("t-2", "c", "d", Money.of("200.50", Currency.RUB), UZ_RU),
                Transfer.create("t-3", "e", "f", Money.of("300", Currency.USD), UZ_KZ),
                Transfer.create("t-4", "g", "h", Money.of("400", Currency.USD), UZ_KZ),
                Transfer.create("t-5", "i", "i", Money.of("500", Currency.USD), UZ_KZ),   // сам себе
                Transfer.create("t-6", "j", "k", Money.of("600", Currency.RUB), UZ_KZ)    // валюта не та
        ).forEach(transferService::submit);

        repository.findById("t-1").orElseThrow().success();
    }

    @Test
    @DisplayName("countByStatus: EnumMap с корректными счётчиками")
    void countByStatus() {
        Map<TransferStatus, Long> counts = reportService.countByStatus();

        assertInstanceOf(EnumMap.class, counts);
        assertEquals(1L, counts.get(TransferStatus.SUCCESS));
        assertEquals(3L, counts.get(TransferStatus.PERFORM));
        assertEquals(2L, counts.get(TransferStatus.FAILED));
        assertEquals(null, counts.get(TransferStatus.PREPARE));
    }

    @Test
    @DisplayName("turnoverByCorridor: суммы складываются по коридорам")
    void turnoverByCorridor() {
        Map<Corridor, BigDecimal> turnover = reportService.turnoverByCorridor();

        assertEquals(2, turnover.size());
        assertEquals(0, new BigDecimal("300.50").compareTo(turnover.get(UZ_RU)));
        assertEquals(0, new BigDecimal("1800.00").compareTo(turnover.get(UZ_KZ)));
    }

    @Test
    @DisplayName("successVsFailed: partitioningBy делит на две группы")
    void successVsFailed() {
        Map<Boolean, List<Transfer>> partition = reportService.successVsFailed();

        assertEquals(2, partition.size());
        assertEquals(4, partition.get(true).size());
        assertEquals(2, partition.get(false).size());
        assertEquals(List.of("t-5", "t-6"),
                partition.get(false).stream().map(Transfer::getId).sorted().toList());
    }
}
