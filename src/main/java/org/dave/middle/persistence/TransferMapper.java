package org.dave.middle.persistence;

import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.model.TransferStatus;
import org.dave.middle.domain.vo.Corridor;
import org.dave.middle.domain.vo.Money;
import org.dave.middle.persistence.entity.ClientEntity;
import org.dave.middle.persistence.entity.CorridorEmbeddable;
import org.dave.middle.persistence.entity.MoneyEmbeddable;
import org.dave.middle.persistence.entity.TransferEntity;
import org.springframework.stereotype.Component;

@Component
public class TransferMapper {

    public TransferEntity toNewEntity(Transfer transfer, ClientEntity sender, ClientEntity receiver) {
        TransferEntity entity = new TransferEntity();
        entity.setId(transfer.getId());
        entity.setSender(sender);
        entity.setReceiver(receiver);
        entity.setMoney(new MoneyEmbeddable(transfer.getMoney().amount(), transfer.getMoney().currency()));
        entity.setCorridor(new CorridorEmbeddable(transfer.getCorridor().from(), transfer.getCorridor().to()));
        entity.changeStatus(transfer.getStatus(), "создана");
        return entity;
    }

    public Transfer toDomain(TransferEntity entity) {
        Transfer transfer = Transfer.create(
                entity.getId(),
                entity.getSender().getId(),
                entity.getReceiver().getId(),
                new Money(entity.getMoney().getAmount(), entity.getMoney().getCurrency()),
                Corridor.of(entity.getCorridor().getFrom(), entity.getCorridor().getTo()));
        applyStatus(transfer, entity.getStatus());
        return transfer;
    }

    private void applyStatus(Transfer transfer, TransferStatus status) {
        switch (status) {
            case PREPARE -> {
            }
            case PERFORM -> transfer.perform();
            case SUCCESS -> {
                transfer.perform();
                transfer.success();
            }
            case FAILED -> transfer.fail();
        }
    }
}
