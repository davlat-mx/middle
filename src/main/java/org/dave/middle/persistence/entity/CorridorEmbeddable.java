package org.dave.middle.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dave.middle.domain.model.Country;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CorridorEmbeddable {

    @Enumerated(EnumType.STRING)
    @Column(name = "corridor_from", nullable = false, length = 2)
    private Country from;

    @Enumerated(EnumType.STRING)
    @Column(name = "corridor_to", nullable = false, length = 2)
    private Country to;
}
