package dev.snehil.cohort.stats;

import dev.snehil.cohort.domain.Category;
import dev.snehil.cohort.domain.Customer;
import dev.snehil.cohort.domain.Order;
import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.domain.Value;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static dev.snehil.cohort.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatsCalculatorTest {
    private final OrderStatsCalculator calc = new OrderStatsCalculator();
    private final Customer customer = new Customer("u1", "bengaluru", LocalDate.of(2026, 1, 1));

    private static Order order(int daysAgo, long rupees, boolean premium, Category... cats) {
        return new Order("o" + daysAgo, "u1", TODAY.minusDays(daysAgo), rupees * 100, Set.of(cats), premium);
    }

    @Test
    void windowsIncludeTodayAndExcludeTheBoundaryDay() {
        UserProfile p = calc.compute(customer, List.of(
                order(0, 100, false, Category.DAIRY),
                order(6, 100, false, Category.DAIRY),
                order(7, 100, false, Category.PRODUCE),   // just outside 7d
                order(29, 200, false, Category.PRODUCE),
                order(30, 300, true, Category.STAPLES),   // just outside 30d
                order(89, 400, false, Category.MEAT),
                order(90, 500, true, Category.MEAT)),     // just outside 90d
                TODAY);

        assertThat(p.get("orders_7d")).contains(Value.num(2));
        assertThat(p.get("orders_30d")).contains(Value.num(4));
        assertThat(p.get("orders_90d")).contains(Value.num(6));
        assertThat(p.get("orders_total")).contains(Value.num(7));
        assertThat(p.get("spend_30d")).contains(Value.num(500));
        assertThat(p.get("spend_90d")).contains(Value.num(1_200));
        assertThat(p.get("avg_order_value_90d")).contains(Value.num(200));
        assertThat(p.get("dairy_orders_30d")).contains(Value.num(2));
        assertThat(p.get("produce_orders_30d")).contains(Value.num(2));
        assertThat(p.get("staples_orders_30d")).contains(Value.num(0));
        assertThat(p.get("categories_30d")).contains(Value.textSet(List.of("dairy", "produce")));
        assertThat(p.get("bought_premium")).contains(Value.bool(true)); // the 30-days-ago staples order
        assertThat(p.get("days_since_last_order")).contains(Value.num(0));
        assertThat(p.get("first_order_date")).contains(Value.date(TODAY.minusDays(90)));
    }

    @Test
    void activeWeeksCountsDistinctWeeksInTheLastEight() {
        UserProfile p = calc.compute(customer, List.of(
                order(0, 1, false, Category.DAIRY), order(1, 1, false, Category.DAIRY), // same week
                order(7, 1, false, Category.DAIRY), order(20, 1, false, Category.DAIRY),
                order(55, 1, false, Category.DAIRY), order(56, 1, false, Category.DAIRY)), // 56 is week 9
                TODAY);
        assertThat(p.get("active_weeks_8w")).contains(Value.num(4));
    }

    @Test
    void noOrdersMeansNoRecencyAttributesButZeroCounts() {
        UserProfile p = calc.compute(customer, List.of(), TODAY);
        assertThat(p.get("days_since_last_order")).isEmpty();
        assertThat(p.get("first_order_week")).isEmpty();
        assertThat(p.get("orders_30d")).contains(Value.num(0));
        assertThat(p.get("bought_premium")).contains(Value.bool(false));
        assertThat(p.get("city")).contains(Value.text("bengaluru"));
    }

    @Test
    void futureOrdersAreIgnored() {
        UserProfile p = calc.compute(customer, List.of(order(-1, 100, false, Category.DAIRY)), TODAY);
        assertThat(p.get("orders_total")).contains(Value.num(0));
    }

    @Test
    void everyComputedAttributeIsInTheSchema() {
        UserProfile p = calc.compute(customer, List.of(order(3, 100, true, Category.DAIRY)), TODAY);
        assertThat(OrderStatsCalculator.SCHEMA.names()).containsAll(p.attributes().keySet());
        for (var e : p.attributes().entrySet()) {
            assertThat(e.getValue().type()).as(e.getKey())
                    .isEqualTo(OrderStatsCalculator.SCHEMA.typeOf(e.getKey()).orElseThrow());
        }
    }

    @Test
    void isoWeekLabel() {
        assertThat(OrderStatsCalculator.isoWeek(LocalDate.of(2026, 10, 22))).isEqualTo("2026-W43");
        assertThat(OrderStatsCalculator.isoWeek(LocalDate.of(2027, 1, 1))).isEqualTo("2026-W53");
    }
}
