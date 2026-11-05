package dev.snehil.cohort;

import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.parse.RuleParser;
import dev.snehil.cohort.rule.EvalContext;
import dev.snehil.cohort.rule.op.OperatorRegistry;
import dev.snehil.cohort.stats.OrderStatsCalculator;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared test data. */
public final class Fixtures {
    private Fixtures() {}

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    public static final EvalContext CTX = EvalContext.on(TODAY);
    public static final RuleParser PARSER = new RuleParser(OperatorRegistry.defaults(), OrderStatsCalculator.SCHEMA);

    /** A weekly buyer who has gone quiet for 20 days, in Bengaluru, buying dairy and produce. */
    public static UserProfile asha() {
        Map<String, Value> a = new HashMap<>();
        a.put("city", Value.text("bengaluru"));
        a.put("signup_date", Value.date(LocalDate.of(2026, 3, 2)));
        a.put("first_order_date", Value.date(LocalDate.of(2026, 3, 4)));
        a.put("last_order_date", Value.date(TODAY.minusDays(20)));
        a.put("first_order_week", Value.text("2026-W10"));
        a.put("days_since_last_order", Value.num(20));
        a.put("orders_7d", Value.num(0));
        a.put("orders_30d", Value.num(6));
        a.put("orders_90d", Value.num(14));
        a.put("orders_total", Value.num(30));
        a.put("spend_30d", Value.num(4_200));
        a.put("spend_90d", Value.num(9_800));
        a.put("avg_order_value_90d", Value.num(700));
        a.put("active_weeks_8w", Value.num(5));
        a.put("bought_premium", Value.bool(false));
        a.put("categories_30d", Value.textSet(List.of("dairy", "produce")));
        a.put("dairy_orders_30d", Value.num(5));
        a.put("produce_orders_30d", Value.num(4));
        a.put("staples_orders_30d", Value.num(1));
        return new UserProfile("asha", a);
    }

    /** A brand-new user with no orders: order-derived attributes are absent. */
    public static UserProfile newbie() {
        return new UserProfile("newbie", Map.of(
                "city", Value.text("pune"),
                "signup_date", Value.date(TODAY.minusDays(1)),
                "orders_30d", Value.num(0),
                "orders_total", Value.num(0)));
    }
}
