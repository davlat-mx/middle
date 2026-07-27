package org.dave.middle.domain.vo;

import org.dave.middle.domain.model.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    @DisplayName("сумма нормализуется до двух знаков")
    void normalizesScale() {
        assertEquals(new BigDecimal("100.00"), Money.of("100", Currency.USD).amount());
        assertEquals(new BigDecimal("100.50"), Money.of("100.5", Currency.USD).amount());
    }

    @Test
    @DisplayName("ноль и отрицательная сумма запрещены")
    void rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("0", Currency.USD));
        assertThrows(IllegalArgumentException.class, () -> Money.of("-1", Currency.USD));
    }

    @Test
    @DisplayName("больше двух знаков после запятой запрещено")
    void rejectsTooManyDecimals() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("10.001", Currency.USD));
    }

    @Test
    @DisplayName("хвостовые нули не считаются лишними знаками")
    void allowsTrailingZeros() {
        assertEquals(new BigDecimal("10.00"), Money.of("10.000", Currency.USD).amount());
    }

    @Test
    @DisplayName("plus складывает одинаковые валюты")
    void plusSameCurrency() {
        Money sum = Money.of("10.50", Currency.UZS).plus(Money.of("0.50", Currency.UZS));
        assertEquals(Money.of("11", Currency.UZS), sum);
    }

    @Test
    @DisplayName("plus на разных валютах бросает исключение")
    void plusDifferentCurrency() {
        Money uzs = Money.of("10", Currency.UZS);
        Money usd = Money.of("10", Currency.USD);
        assertThrows(IllegalArgumentException.class, () -> uzs.plus(usd));
    }

    @Test
    @DisplayName("равенство по значению")
    void valueEquality() {
        assertEquals(Money.of("10", Currency.RUB), Money.of("10.00", Currency.RUB));
    }
}
