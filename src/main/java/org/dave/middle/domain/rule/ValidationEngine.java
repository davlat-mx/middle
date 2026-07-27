package org.dave.middle.domain.rule;

import org.dave.middle.domain.model.Transfer;

import java.util.List;

public final class ValidationEngine {

    private final List<TransferRule> rules;

    public ValidationEngine(List<TransferRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static ValidationEngine withDefaults() {
        return new ValidationEngine(List.of(
                CurrencyAllowedRule.withDefaults(),
                new SameClientRule()
        ));
    }

    public List<String> validate(Transfer transfer) {
        return rules.stream()
                .map(rule -> rule.check(transfer))
                .filter(result -> !result.valid())
                .map(ValidationResult::error)
                .toList();
    }

    public boolean isValid(Transfer transfer) {
        return validate(transfer).isEmpty();
    }
}
