package dev.snehil.cohort.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One delivered order. Money is kept in paisa as a {@code long}, never as a floating-point number.
 *
 * @param categories the categories present in the basket
 * @param premium    whether the basket contained at least one premium-tier item
 */
public record Order(String id, String userId, LocalDate date, long amountPaisa,
                    Set<Category> categories, boolean premium) {
    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(categories, "categories");
        if (amountPaisa < 0) throw new IllegalArgumentException("order amount can't be negative: " + id);
        if (categories.isEmpty()) throw new IllegalArgumentException("order needs at least one category: " + id);
        categories = Set.copyOf(EnumSet.copyOf(categories)); // defensive, immutable copy
    }
}
