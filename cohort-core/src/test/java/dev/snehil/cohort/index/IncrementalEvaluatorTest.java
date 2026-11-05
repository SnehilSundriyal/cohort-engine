package dev.snehil.cohort.index;

import dev.snehil.cohort.catalog.DefaultCohorts;
import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.domain.Order;
import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.engine.AssignmentStrategy;
import dev.snehil.cohort.gen.SyntheticDataGenerator;
import dev.snehil.cohort.rule.EvalContext;
import dev.snehil.cohort.stats.OrderStatsCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import static dev.snehil.cohort.Fixtures.CTX;
import static dev.snehil.cohort.Fixtures.PARSER;
import static dev.snehil.cohort.Fixtures.TODAY;
import static dev.snehil.cohort.Fixtures.asha;
import static org.assertj.core.api.Assertions.assertThat;

class IncrementalEvaluatorTest {

    private static Cohort cohort(String id, String rule) {
        return new Cohort(id, id, 1, rule, PARSER.parse(rule), 1);
    }

    private final List<Cohort> cohorts = List.of(
            cohort("quiet", "orders_30d >= 4 AND days_since_last_order > 14"),
            cohort("south", "city IN ('bengaluru', 'hyderabad')"),
            cohort("recent", "last_order_date WITHIN_DAYS 21"),
            cohort("premium", "bought_premium = true"));

    private static UserProfile with(UserProfile p, String attribute, Value value) {
        var attrs = new HashMap<>(p.attributes());
        attrs.put(attribute, value);
        return new UserProfile(p.userId(), attrs);
    }

    @Test
    void firstUpdateEvaluatesEverythingAndEmitsJoins() {
        var inc = new IncrementalEvaluator(cohorts);
        List<MembershipEvent> heard = new ArrayList<>();
        inc.addListener(heard::add);

        var update = inc.apply(asha(), CTX);

        assertThat(update.cohortsEvaluated()).isEqualTo(4);
        assertThat(inc.membership("asha")).contains(Set.of("quiet", "south", "recent"));
        assertThat(heard).extracting(MembershipEvent::cohortId).containsExactlyInAnyOrder("quiet", "south", "recent");
        assertThat(heard).allMatch(e -> e.kind() == MembershipEvent.Kind.JOINED);
    }

    @Test
    void onlyCohortsReadingChangedAttributesAreReEvaluated() {
        var inc = new IncrementalEvaluator(cohorts);
        inc.apply(asha(), CTX);
        List<MembershipEvent> heard = new ArrayList<>();
        inc.addListener(heard::add);

        var update = inc.apply(with(asha(), "city", Value.text("pune")), CTX);

        assertThat(update.changedAttributes()).containsExactly("city");
        assertThat(update.cohortsEvaluated()).isEqualTo(1); // only "south"
        assertThat(heard).containsExactly(new MembershipEvent("asha", "south", MembershipEvent.Kind.LEFT, TODAY));
    }

    @Test
    void unchangedProfileOnTheSameDayEvaluatesNothing() {
        var inc = new IncrementalEvaluator(cohorts);
        inc.apply(asha(), CTX);
        assertThat(inc.apply(asha(), CTX).cohortsEvaluated()).isZero();
    }

    @Test
    void aNewDayReEvaluatesTimeDependentCohortsEvenWithoutChanges() {
        var inc = new IncrementalEvaluator(cohorts);
        inc.apply(asha(), CTX); // last order 20 days ago: in "recent" (WITHIN_DAYS 21)

        var update = inc.apply(asha(), EvalContext.on(TODAY.plusDays(2))); // now 22 days ago

        assertThat(update.cohortsEvaluated()).isEqualTo(1);
        assertThat(update.events()).containsExactly(
                new MembershipEvent("asha", "recent", MembershipEvent.Kind.LEFT, TODAY.plusDays(2)));
    }

    @Test
    void countersTrackWorkSaved() {
        var inc = new IncrementalEvaluator(cohorts);
        inc.apply(asha(), CTX);
        inc.resetCounters();
        inc.apply(with(asha(), "bought_premium", Value.bool(true)), CTX);
        assertThat(inc.evaluationsPerformed()).isEqualTo(1);
        assertThat(inc.evaluationsSkipped()).isEqualTo(3);
    }

    /**
     * The safety property: after any sequence of updates, incremental membership equals a full
     * re-evaluation. Random users, random new orders, random day jumps, the real default catalog.
     */
    @Test
    void alwaysAgreesWithAFullReScore() {
        List<Cohort> catalog = DefaultCohorts.load(PARSER);
        var inc = new IncrementalEvaluator(catalog);
        var gen = new SyntheticDataGenerator(7, TODAY);
        var calc = new OrderStatsCalculator();
        var all = new AssignmentStrategy.AllMatching();
        var rnd = new SplittableRandom(99);

        int users = 300;
        List<SyntheticDataGenerator.GeneratedUser> population = new ArrayList<>();
        List<List<Order>> orders = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            population.add(gen.user(i));
            orders.add(new ArrayList<>(population.get(i).orders()));
        }

        LocalDate day = TODAY;
        for (int step = 0; step < 3_000; step++) {
            if (rnd.nextInt(200) == 0) day = day.plusDays(rnd.nextInt(1, 10));
            int i = rnd.nextInt(users);
            var u = population.get(i);
            if (rnd.nextBoolean()) orders.get(i).add(gen.extraOrder(u, day, step));
            var customer = rnd.nextInt(20) == 0 ? u.customer().withCity("hyderabad") : u.customer();
            UserProfile p = calc.compute(customer, orders.get(i), day);
            EvalContext ctx = EvalContext.on(day);

            inc.apply(p, ctx);

            Set<String> expected = new HashSet<>(all.assign(p, catalog, ctx).stream().map(Cohort::id).toList());
            assertThat(inc.membership(p.userId())).as("step %d, %s", step, p.userId()).contains(expected);
        }
    }
}
