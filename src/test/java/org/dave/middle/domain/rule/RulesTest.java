package org.dave.middle.domain.rule;

import org.dave.middle.domain.model.Country;
import org.dave.middle.domain.model.Currency;
import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.vo.Corridor;
import org.dave.middle.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {

    private static Transfer transfer(String senderId, String receiverId, Currency currency, Corridor corridor) {
        return Transfer.create("t-1", senderId, receiverId, Money.of("100", currency), corridor);
    }

    private static final Corridor UZ_KZ = Corridor.of(Country.UZ, Country.KZ);

    @Test
    @DisplayName("CurrencyAllowedRule: разрешённая валюта проходит")
    void currencyOk() {
        ValidationResult result = CurrencyAllowedRule.withDefaults()
                .check(transfer("a", "b", Currency.USD, UZ_KZ));
        assertTrue(result.valid());
    }

    @Test
    @DisplayName("CurrencyAllowedRule: запрещённая валюта отклоняется")
    void currencyRejected() {
        ValidationResult result = CurrencyAllowedRule.withDefaults()
                .check(transfer("a", "b", Currency.RUB, UZ_KZ));
        assertFalse(result.valid());
        assertTrue(result.error().contains("RUB"));
    }

    @Test
    @DisplayName("CurrencyAllowedRule: необслуживаемый коридор отклоняется")
    void unknownCorridorRejected() {
        CurrencyAllowedRule rule = new CurrencyAllowedRule(
                Map.of(UZ_KZ, EnumSet.of(Currency.USD)));
        ValidationResult result = rule.check(
                transfer("a", "b", Currency.USD, Corridor.of(Country.RU, Country.KZ)));
        assertFalse(result.valid());
        assertTrue(result.error().contains("не обслуживается"));
    }

    @Test
    @DisplayName("SameClientRule: разные клиенты проходят")
    void differentClientsOk() {
        assertTrue(new SameClientRule().check(transfer("a", "b", Currency.USD, UZ_KZ)).valid());
    }

    @Test
    @DisplayName("SameClientRule: перевод самому себе отклоняется")
    void sameClientRejected() {
        ValidationResult result = new SameClientRule().check(transfer("a", "a", Currency.USD, UZ_KZ));
        assertFalse(result.valid());
        assertTrue(result.error().contains("самому себе"));
    }

    @Test
    @DisplayName("and(): второе правило не вызывается, если первое упало")
    void andShortCircuits() {
        TransferRule alwaysFails = t -> ValidationResult.fail("первое");
        TransferRule shouldNotRun = t -> {
            throw new AssertionError("второе правило не должно вызываться");
        };
        ValidationResult result = alwaysFails.and(shouldNotRun).check(transfer("a", "b", Currency.USD, UZ_KZ));
        assertFalse(result.valid());
        assertEquals("первое", result.error());
    }

    @Test
    @DisplayName("and(): при успехе первого возвращается результат второго")
    void andChains() {
        TransferRule ok = t -> ValidationResult.ok();
        TransferRule fails = t -> ValidationResult.fail("второе");
        ValidationResult result = ok.and(fails).check(transfer("a", "b", Currency.USD, UZ_KZ));
        assertEquals("второе", result.error());
    }

    @Test
    @DisplayName("ValidationEngine собирает все ошибки, а не первую")
    void engineCollectsAllErrors() {
        List<String> errors = ValidationEngine.withDefaults()
                .validate(transfer("a", "a", Currency.RUB, UZ_KZ));
        assertEquals(2, errors.size());
    }

    @Test
    @DisplayName("ValidationEngine: корректная заявка без ошибок")
    void engineAcceptsValid() {
        assertTrue(ValidationEngine.withDefaults().isValid(transfer("a", "b", Currency.USD, UZ_KZ)));
    }
}
