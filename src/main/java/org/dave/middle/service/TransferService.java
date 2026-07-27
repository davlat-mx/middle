package org.dave.middle.service;

import lombok.RequiredArgsConstructor;
import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.rule.ValidationEngine;
import org.dave.middle.repository.TransferRepository;

import java.util.List;

@RequiredArgsConstructor
public class TransferService {

    private final ValidationEngine validationEngine;
    private final TransferRepository repository;

    public SubmitResult submit(Transfer transfer) {
        List<String> errors = validationEngine.validate(transfer);
        if (errors.isEmpty()) {
            transfer.perform();
        } else {
            transfer.fail();
        }
        repository.save(transfer);
        return new SubmitResult(transfer, errors);
    }

    public record SubmitResult(Transfer transfer, List<String> errors) {
        public boolean accepted() {
            return errors.isEmpty();
        }
    }
}
