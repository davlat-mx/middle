package org.dave.middle.repository;

import org.dave.middle.domain.model.Transfer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TransferRepository {

    private final Map<String, Transfer> storage = new ConcurrentHashMap<>();

    public Transfer save(Transfer transfer) {
        storage.put(transfer.getId(), transfer);
        return transfer;
    }

    public Optional<Transfer> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    public List<Transfer> findAll() {
        return List.copyOf(storage.values());
    }

    public int count() {
        return storage.size();
    }
}
