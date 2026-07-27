package org.dave.middle.domain.vo;

import org.dave.middle.domain.model.Country;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorridorTest {

    @Test
    @DisplayName("коридор внутри одной страны запрещён")
    void rejectsSameCountry() {
        assertThrows(IllegalArgumentException.class, () -> Corridor.of(Country.UZ, Country.UZ));
    }

    @Test
    @DisplayName("null не допускается")
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> Corridor.of(null, Country.UZ));
        assertThrows(NullPointerException.class, () -> Corridor.of(Country.UZ, null));
    }

    @Test
    @DisplayName("равенство по значению, направление важно")
    void valueEqualityIsDirectional() {
        assertEquals(Corridor.of(Country.UZ, Country.RU), Corridor.of(Country.UZ, Country.RU));
        assertNotEquals(Corridor.of(Country.UZ, Country.RU), Corridor.of(Country.RU, Country.UZ));
    }
}
