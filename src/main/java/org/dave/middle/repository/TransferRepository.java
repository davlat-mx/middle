package org.dave.middle.repository;

import org.dave.middle.domain.model.Transfer;

import java.util.List;
import java.util.Optional;


public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(String id);

    List<Transfer> findAll();

    int count();
}
