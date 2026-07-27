package org.dave.middle.domain.vo;

import org.dave.middle.domain.model.Country;

import java.util.Objects;


public record Corridor(Country from, Country to) {

    public Corridor {
        Objects.requireNonNull(from, "from не может быть null");
        Objects.requireNonNull(to, "to не может быть null");
        if (from == to) {
            throw new IllegalArgumentException("Коридор внутри одной страны недопустим: " + from);
        }
    }

    public static Corridor of(Country from, Country to) {
        return new Corridor(from, to);
    }

    @Override
    public String toString() {
        return from + "->" + to;
    }
}
