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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferServiceTest {

    private TransferRepository repository;
    private TransferService service;

    @BeforeEach
    void setUp() {
        repository = new TransferRepository();
        service = new TransferService(ValidationEngine.withDefaults(), repository);
    }

    @Test
    @DisplayName("валидная заявка уходит в PERFORM и сохраняется")
    void validGoesToPerform() {
        Transfer transfer = Transfer.create("t-1", "a", "b",
                Money.of("100", Currency.USD), Corridor.of(Country.UZ, Country.KZ));

        TransferService.SubmitResult result = service.submit(transfer);

        assertTrue(result.accepted());
        assertEquals(TransferStatus.PERFORM, transfer.getStatus());
        assertEquals(transfer, repository.findById("t-1").orElseThrow());
    }

    @Test
    @DisplayName("невалидная заявка уходит в FAILED, но тоже сохраняется")
    void invalidGoesToFailed() {
        Transfer transfer = Transfer.create("t-2", "a", "a",
                Money.of("100", Currency.RUB), Corridor.of(Country.UZ, Country.KZ));

        TransferService.SubmitResult result = service.submit(transfer);

        assertFalse(result.accepted());
        assertEquals(2, result.errors().size());
        assertEquals(TransferStatus.FAILED, transfer.getStatus());
        assertTrue(repository.findById("t-2").isPresent());
    }
}
