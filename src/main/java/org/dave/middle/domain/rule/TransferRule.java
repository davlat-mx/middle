package org.dave.middle.domain.rule;

import org.dave.middle.domain.model.Transfer;

@FunctionalInterface
public interface TransferRule {

    ValidationResult check(Transfer transfer);

    default TransferRule and(TransferRule next) {
        return transfer -> {
            ValidationResult result = check(transfer);
            return result.valid() ? next.check(transfer) : result;
        };
    }
}
