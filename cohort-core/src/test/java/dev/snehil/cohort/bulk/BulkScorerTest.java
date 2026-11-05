package dev.snehil.cohort.bulk;

import dev.snehil.cohort.catalog.DefaultCohorts;
import dev.snehil.cohort.engine.AssignmentStrategy;
import dev.snehil.cohort.engine.CohortEngine;
import dev.snehil.cohort.gen.SyntheticDataGenerator;
import dev.snehil.cohort.stats.OrderStatsCalculator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.IntStream;

import static dev.snehil.cohort.Fixtures.CTX;
import static dev.snehil.cohort.Fixtures.PARSER;
import static dev.snehil.cohort.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

class BulkScorerTest {
    private static final CohortEngine ENGINE = new CohortEngine(DefaultCohorts.load(PARSER));
    private static final SyntheticDataGenerator GEN = new SyntheticDataGenerator(1, TODAY);
    // 5,000 users: not a multiple of the chunk size, so the last chunk is partial.
    private static final List<SyntheticDataGenerator.GeneratedUser> USERS =
            IntStream.range(0, 5_000).mapToObj(GEN::user).toList();

    private static BulkScorer<SyntheticDataGenerator.GeneratedUser> scorer(AssignmentStrategy strategy) {
        var calc = new OrderStatsCalculator();
        return new BulkScorer<>(ENGINE, strategy, u -> calc.compute(u.customer(), u.orders(), TODAY));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 8})
    void parallelGivesExactlyTheSameResultAsSequential(int threads) {
        var s = scorer(new AssignmentStrategy.AllMatching());
        var one = s.score(USERS, CTX, 1);
        var many = s.score(USERS, CTX, threads);
        assertThat(many.assignments()).isEqualTo(one.assignments());
        assertThat(many.countsByCohort()).isEqualTo(one.countsByCohort());
        assertThat(one.users()).isEqualTo(5_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void priorityModeAssignsAtMostOneCohort(int threads) {
        var result = scorer(new AssignmentStrategy.HighestPriority()).score(USERS, CTX, threads);
        assertThat(result.assignments()).allSatisfy(a -> assertThat(a).hasSizeLessThanOrEqualTo(1));
        long assigned = result.countsByCohort().values().stream().mapToLong(Long::longValue).sum();
        assertThat(assigned).isEqualTo(result.assignments().stream().filter(a -> !a.isEmpty()).count());
    }
}
