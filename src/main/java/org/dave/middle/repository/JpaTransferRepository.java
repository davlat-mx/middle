package org.dave.middle.repository;

import org.dave.middle.domain.model.Transfer;
import org.dave.middle.persistence.TransferMapper;
import org.dave.middle.persistence.entity.ClientEntity;
import org.dave.middle.persistence.entity.TransferEntity;
import org.dave.middle.persistence.repository.ClientEntityRepository;
import org.dave.middle.persistence.repository.TransferEntityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaTransferRepository implements TransferRepository {

    private final TransferEntityRepository transfers;
    private final ClientEntityRepository clients;
    private final TransferMapper mapper;

    public JpaTransferRepository(TransferEntityRepository transfers,
                                 ClientEntityRepository clients,
                                 TransferMapper mapper) {
        this.transfers = transfers;
        this.clients = clients;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Transfer save(Transfer transfer) {
        TransferEntity entity = transfers.findById(transfer.getId()).orElse(null);
        if (entity == null) {
            ClientEntity sender = resolveClient(transfer.getSenderId(), transfer.getCorridor().from());
            ClientEntity receiver = resolveClient(transfer.getReceiverId(), transfer.getCorridor().to());
            entity = mapper.toNewEntity(transfer, sender, receiver);
        } else if (entity.getStatus() != transfer.getStatus()) {
            entity.changeStatus(transfer.getStatus(), null);
        }
        transfers.save(entity);
        return transfer;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transfer> findById(String id) {
        return transfers.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transfer> findAll() {
        return transfers.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int count() {
        return (int) transfers.count();
    }

    private ClientEntity resolveClient(String id, org.dave.middle.domain.model.Country country) {
        return clients.findById(id)
                .orElseGet(() -> clients.save(new ClientEntity(id, id, country)));
    }
}
