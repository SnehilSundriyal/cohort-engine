package dev.snehil.cohort.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Profile fields that don't come from orders. */
public record Customer(String id, String city, LocalDate signupDate) {
    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(signupDate, "signupDate");
        if (id.isBlank()) throw new IllegalArgumentException("customer id can't be blank");
    }

    public Customer withCity(String newCity) {
        return new Customer(id, newCity, signupDate);
    }
}
