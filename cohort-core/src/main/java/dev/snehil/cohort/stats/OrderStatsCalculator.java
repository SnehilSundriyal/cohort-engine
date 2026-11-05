package dev.snehil.cohort.stats;

import dev.snehil.cohort.domain.AttributeSchema;
import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Category;
import dev.snehil.cohort.domain.Customer;
import dev.snehil.cohort.domain.Order;
import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.domain.Value;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns a customer's raw orders into the derived attributes rules are written against
 * (recency, frequency, spend, category mix). One pass over the orders; no state, so it's
 * thread-safe and used directly by the parallel bulk scorer.
 *
 * <p>Windows are "the last N days including today": an order placed today is in orders_7d,
 * one placed exactly 30 days ago is not in orders_30d.
 *
 * <p>Order-derived attributes are absent, not zero, when they're meaningless: a user with no
 * orders has no {@code days_since_last_order}. Counts and spend are always present (zero).
 */
public final class OrderStatsCalculator {

    public static final AttributeSchema SCHEMA = buildSchema();

    private static AttributeSchema buildSchema() {
        Map<String, AttributeType> m = new LinkedHashMap<>();
        m.put("city", AttributeType.TEXT);
        m.put("signup_date", AttributeType.DATE);
        m.put("first_order_date", AttributeType.DATE);
        m.put("last_order_date", AttributeType.DATE);
        m.put("first_order_week", AttributeType.TEXT);
        m.put("days_since_last_order", AttributeType.NUMBER);
        m.put("orders_7d", AttributeType.NUMBER);
        m.put("orders_30d", AttributeType.NUMBER);
        m.put("orders_90d", AttributeType.NUMBER);
        m.put("orders_total", AttributeType.NUMBER);
        m.put("spend_30d", AttributeType.NUMBER);
        m.put("spend_90d", AttributeType.NUMBER);
        m.put("avg_order_value_90d", AttributeType.NUMBER);
        m.put("active_weeks_8w", AttributeType.NUMBER);
        m.put("bought_premium", AttributeType.BOOLEAN);
        m.put("categories_30d", AttributeType.TEXT_SET);
        for (Category c : Category.values()) m.put(c.key() + "_orders_30d", AttributeType.NUMBER);
        return new AttributeSchema(m);
    }

    public UserProfile compute(Customer customer, List<Order> orders, LocalDate today) {
        Map<String, Value> a = new HashMap<>();
        a.put("city", Value.text(customer.city()));
        a.put("signup_date", Value.date(customer.signupDate()));

        int orders7 = 0, orders30 = 0, orders90 = 0, total = 0;
        long spend30Paisa = 0, spend90Paisa = 0;
        boolean premium = false;
        LocalDate first = null, last = null;
        Map<Category, Integer> categoryCounts30 = new EnumMap<>(Category.class);
        Set<String> categories30 = new HashSet<>();
        Set<Long> activeWeeks = new HashSet<>();

        for (Order o : orders) {
            long age = ChronoUnit.DAYS.between(o.date(), today);
            if (age < 0) continue; // future-dated orders don't count yet
            total++;
            if (first == null || o.date().isBefore(first)) first = o.date();
            if (last == null || o.date().isAfter(last)) last = o.date();
            if (age < 7) orders7++;
            if (age < 56) activeWeeks.add(age / 7);
            if (age < 30) {
                orders30++;
                spend30Paisa += o.amountPaisa();
                for (Category c : o.categories()) {
                    categoryCounts30.merge(c, 1, Integer::sum);
                    categories30.add(c.key());
                }
            }
            if (age < 90) {
                orders90++;
                spend90Paisa += o.amountPaisa();
                premium |= o.premium();
            }
        }

        a.put("orders_7d", Value.num(orders7));
        a.put("orders_30d", Value.num(orders30));
        a.put("orders_90d", Value.num(orders90));
        a.put("orders_total", Value.num(total));
        a.put("spend_30d", Value.num(spend30Paisa / 100));   // rupees in rules, paisa in orders
        a.put("spend_90d", Value.num(spend90Paisa / 100));
        a.put("avg_order_value_90d", Value.num(orders90 == 0 ? 0 : spend90Paisa / 100 / orders90));
        a.put("active_weeks_8w", Value.num(activeWeeks.size()));
        a.put("bought_premium", Value.bool(premium));
        a.put("categories_30d", Value.textSet(new TreeSet<>(categories30)));
        for (Category c : Category.values()) {
            a.put(c.key() + "_orders_30d", Value.num(categoryCounts30.getOrDefault(c, 0)));
        }
        if (last != null) {
            a.put("first_order_date", Value.date(first));
            a.put("last_order_date", Value.date(last));
            a.put("days_since_last_order", Value.num(ChronoUnit.DAYS.between(last, today)));
            a.put("first_order_week", Value.text(isoWeek(first)));
        }
        return new UserProfile(customer.id(), a);
    }

    /** ISO week label, e.g. {@code 2026-W43}. Used for acquisition-week cohorts. */
    public static String isoWeek(LocalDate date) {
        return String.format("%d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
