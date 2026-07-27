package org.dave.middle.domain.model;

import org.dave.middle.domain.vo.Corridor;
import org.dave.middle.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferTest {

    private static Transfer newTransfer() {
        return Transfer.create("t-1", "sender", "receiver",
                Money.of("100", Currency.USD), Corridor.of(Country.UZ, Country.KZ));
    }

    @Test
    @DisplayName("создаётся в статусе PREPARE")
    void createdInPrepare() {
        assertEquals(TransferStatus.PREPARE, newTransfer().getStatus());
    }

    @Test
    @DisplayName("пустые идентификаторы запрещены")
    void rejectsBlankIds() {
        assertThrows(IllegalArgumentException.class, () -> Transfer.create("t-1", "  ", "receiver",
                Money.of("100", Currency.USD), Corridor.of(Country.UZ, Country.KZ)));
    }

    @Test
    @DisplayName("id генерируется, если не задан явно")
    void generatesId() {
        Transfer first = Transfer.create("sender", "receiver",
                Money.of("100", Currency.USD), Corridor.of(Country.UZ, Country.KZ));
        Transfer second = Transfer.create("sender", "receiver",
                Money.of("100", Currency.USD), Corridor.of(Country.UZ, Country.KZ));

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    @DisplayName("perform / success / fail меняют статус")
    void changesStatus() {
        Transfer transfer = newTransfer();

        transfer.perform();
        assertEquals(TransferStatus.PERFORM, transfer.getStatus());

        transfer.success();
        assertEquals(TransferStatus.SUCCESS, transfer.getStatus());

        transfer.fail();
        assertEquals(TransferStatus.FAILED, transfer.getStatus());
    }

    @Test
    @DisplayName("равенство по id")
    void equalityById() {
        Transfer first = newTransfer();
        Transfer second = newTransfer();
        second.fail();

        assertEquals(first, second);
    }
}
