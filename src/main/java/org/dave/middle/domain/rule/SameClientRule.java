package org.dave.middle.domain.rule;

import org.dave.middle.domain.model.Transfer;


public final class SameClientRule implements TransferRule {

    @Override
    public ValidationResult check(Transfer transfer) {
        if (transfer.getSenderId().equals(transfer.getReceiverId())) {
            return ValidationResult.fail("Перевод самому себе запрещён: " + transfer.getSenderId());
        }
        return ValidationResult.ok();
    }
}
