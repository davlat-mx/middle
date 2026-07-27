package org.dave.middle.domain.rule;

import org.dave.middle.domain.model.Country;
import org.dave.middle.domain.model.Currency;
import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.vo.Corridor;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class CurrencyAllowedRule implements TransferRule {

    private final Map<Corridor, Set<Currency>> allowedByCorridor;

    public CurrencyAllowedRule(Map<Corridor, Set<Currency>> allowedByCorridor) {
        this.allowedByCorridor = Map.copyOf(allowedByCorridor);
    }

    public static CurrencyAllowedRule withDefaults() {
        return new CurrencyAllowedRule(Map.of(
                new Corridor(Country.UZ, Country.RU), EnumSet.of(Currency.UZS, Currency.RUB),
                new Corridor(Country.RU, Country.UZ), EnumSet.of(Currency.RUB, Currency.UZS),
                new Corridor(Country.UZ, Country.KZ), EnumSet.of(Currency.USD),
                new Corridor(Country.KZ, Country.UZ), EnumSet.of(Currency.USD),
                new Corridor(Country.RU, Country.KZ), EnumSet.of(Currency.RUB, Currency.USD),
                new Corridor(Country.KZ, Country.RU), EnumSet.of(Currency.RUB, Currency.USD)
        ));
    }

    @Override
    public ValidationResult check(Transfer transfer) {
        Corridor corridor = transfer.getCorridor();
        Currency currency = transfer.getMoney().currency();
        Set<Currency> allowed = allowedByCorridor.get(corridor);

        if (allowed == null) {
            return ValidationResult.fail("Коридор " + corridor + " не обслуживается");
        }
        if (!allowed.contains(currency)) {
            return ValidationResult.fail(
                    "Валюта " + currency + " недопустима для коридора " + corridor + ", разрешено: " + allowed);
        }
        return ValidationResult.ok();
    }
}
