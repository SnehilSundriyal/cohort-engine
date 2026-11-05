package dev.snehil.cohort.domain;

import java.util.Locale;

/** Grocery categories. Each one produces a {@code <key>_orders_30d} attribute. */
public enum Category {
    DAIRY, PRODUCE, BAKERY, STAPLES, MEAT, SNACKS, BEVERAGES, PERSONAL_CARE;

    /** Lower-case name used in attribute names and rule text, e.g. {@code dairy}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
