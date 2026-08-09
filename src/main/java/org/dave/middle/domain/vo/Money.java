package org.dave.middle.domain.vo;

import org.dave.middle.domain.model.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    private static final int MAX_SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount не может быть null");
        Objects.requireNonNull(currency, "currency не может быть null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше нуля, получено: " + amount);
        }
        if (amount.stripTrailingZeros().scale() > MAX_SCALE) {
            throw new IllegalArgumentException("Не больше " + MAX_SCALE + " знаков после запятой, получено: " + amount);
        }
        amount = amount.setScale(MAX_SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "other не может быть null");
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "Нельзя складывать разные валюты: " + currency + " и " + other.currency);
        }
        return new Money(amount.add(other.amount), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
