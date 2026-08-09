package org.dave.middle.persistence.projection;

import org.dave.middle.domain.model.TransferStatus;
import org.dave.middle.persistence.entity.MoneyEmbeddable;

public interface TransferSummary {

    String getId();

    TransferStatus getStatus();

    MoneyEmbeddable getMoney();
}
