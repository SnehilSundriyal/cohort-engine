package dev.snehil.cohort.engine;

import dev.snehil.cohort.catalog.DefaultCohorts;
import dev.snehil.cohort.domain.Cohort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.snehil.cohort.Fixtures.CTX;
import static dev.snehil.cohort.Fixtures.PARSER;
import static dev.snehil.cohort.Fixtures.asha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CohortEngineTest {

    private static Cohort cohort(String id, int priority, String rule) {
        return new Cohort(id, id, priority, rule, PARSER.parse(rule), 1);
    }

    private final CohortEngine engine = new CohortEngine(List.of(
            cohort("quiet-regular", 50, "orders_30d >= 4 AND days_since_last_order > 14"),
            cohort("bengaluru", 10, "city = 'bengaluru'"),
            cohort("dairy", 30, "categories_30d CONTAINS 'dairy'"),
            cohort("premium", 90, "bought_premium = true")));

    @Test
    void allMatchingReturnsEveryMatchHighestPriorityFirst() {
        var eval = engine.evaluate(asha(), new AssignmentStrategy.AllMatching(), CTX);
        assertThat(eval.cohortIds()).containsExactly("quiet-regular", "dairy", "bengaluru");
        assertThat(eval.matches()).allSatisfy(m -> assertThat(m.trace().passed()).isTrue());
    }

    @Test
    void highestPriorityReturnsExactlyOne() {
        var eval = engine.evaluate(asha(), new AssignmentStrategy.HighestPriority(), CTX);
        assertThat(eval.cohortIds()).containsExactly("quiet-regular");
    }

    @Test
    void tiesAreBrokenById() {
        var tied = new CohortEngine(List.of(cohort("b", 5, "orders_30d > 0"), cohort("a", 5, "orders_30d > 0")));
        assertThat(tied.evaluate(asha(), new AssignmentStrategy.HighestPriority(), CTX).cohortIds()).containsExactly("a");
    }

    @Test
    void explainWorksForCohortsTheUserIsNotIn() {
        var trace = engine.explain(asha(), "premium", CTX);
        assertThat(trace.passed()).isFalse();
        assertThat(trace.detail()).isEqualTo("actual: false");
        assertThatThrownBy(() -> engine.explain(asha(), "nope", CTX)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modesByName() {
        assertThat(AssignmentStrategy.fromName("all")).isInstanceOf(AssignmentStrategy.AllMatching.class);
        assertThat(AssignmentStrategy.fromName("PRIORITY")).isInstanceOf(AssignmentStrategy.HighestPriority.class);
        assertThatThrownBy(() -> AssignmentStrategy.fromName("some")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultCatalogParsesWithUniqueIds() {
        List<Cohort> defaults = DefaultCohorts.load(PARSER);
        assertThat(defaults).hasSizeGreaterThanOrEqualTo(10);
        assertThat(defaults).extracting(Cohort::id).doesNotHaveDuplicates();
    }

    @Test
    void slugs() {
        assertThat(Cohort.slugOf("At-risk regulars!")).isEqualTo("at-risk-regulars");
        assertThat(Cohort.slugOf("  Acquired 2026-W43 ")).isEqualTo("acquired-2026-w43");
        assertThatThrownBy(() -> Cohort.slugOf("!!!")).isInstanceOf(IllegalArgumentException.class);
    }
}
