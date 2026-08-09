package org.dave.middle.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dave.middle.domain.model.TransferStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private TransferEntity transfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private TransferStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private TransferStatus toStatus;

    @Column(name = "at", nullable = false)
    private LocalDateTime at;

    @Column(name = "note")
    private String note;

    public TransferEventEntity(TransferEntity transfer, TransferStatus fromStatus,
                               TransferStatus toStatus, LocalDateTime at, String note) {
        this.transfer = transfer;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.at = at;
        this.note = note;
    }
}
