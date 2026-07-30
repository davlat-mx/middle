package org.dave.middle.queue;

import org.dave.middle.domain.model.Transfer;

@FunctionalInterface
public interface TransferExecutor {

    void execute(Transfer transfer);

    static TransferExecutor alwaysOk() {
        return transfer -> {
        };
    }
}
