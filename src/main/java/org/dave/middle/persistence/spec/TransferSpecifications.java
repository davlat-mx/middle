package org.dave.middle.persistence.spec;

import org.dave.middle.domain.model.Country;
import org.dave.middle.domain.model.TransferStatus;
import org.dave.middle.persistence.entity.TransferEntity;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class TransferSpecifications {

    private TransferSpecifications() {
    }

    public static Specification<TransferEntity> hasStatus(TransferStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<TransferEntity> senderId(String senderId) {
        return (root, query, cb) ->
                senderId == null ? null : cb.equal(root.get("sender").get("id"), senderId);
    }

    public static Specification<TransferEntity> corridorFrom(Country from) {
        return (root, query, cb) ->
                from == null ? null : cb.equal(root.get("corridor").get("from"), from);
    }

    public static Specification<TransferEntity> amountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            var amount = root.get("money").<BigDecimal>get("amount");
            if (min != null && max != null) {
                return cb.between(amount, min, max);
            }
            if (min != null) {
                return cb.greaterThanOrEqualTo(amount, min);
            }
            if (max != null) {
                return cb.lessThanOrEqualTo(amount, max);
            }
            return null;
        };
    }
}
