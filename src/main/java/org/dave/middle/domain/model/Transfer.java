package org.dave.middle.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.dave.middle.domain.vo.Corridor;
import org.dave.middle.domain.vo.Money;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@EqualsAndHashCode(of = "id")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Transfer {

    private final String id;
    private final String senderId;
    private final String receiverId;
    private final Money money;
    private final Corridor corridor;
    private final LocalDateTime createdAt;

    private TransferStatus status;

    public static Transfer create(String senderId, String receiverId, Money money, Corridor corridor) {
        return create(UUID.randomUUID().toString(), senderId, receiverId, money, corridor);
    }

    public static Transfer create(String id,
                                  String senderId,
                                  String receiverId,
                                  Money money,
                                  Corridor corridor) {
        requireText(id, "id");
        requireText(senderId, "senderId");
        requireText(receiverId, "receiverId");
        Objects.requireNonNull(money, "money не может быть null");
        Objects.requireNonNull(corridor, "corridor не может быть null");
        return new Transfer(id, senderId, receiverId, money, corridor,
                LocalDateTime.now(), TransferStatus.PREPARE);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " не может быть пустым");
        }
    }

    public void perform() {
        this.status = TransferStatus.PERFORM;
    }

    public void success() {
        this.status = TransferStatus.SUCCESS;
    }

    public void fail() {
        this.status = TransferStatus.FAILED;
    }

    @Override
    public String toString() {
        return "Transfer[" + id + " " + senderId + "->" + receiverId
                + " " + money + " " + corridor + " " + status + "]";
    }
}
