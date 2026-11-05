package dev.snehil.cohort.repo;

import dev.snehil.cohort.domain.Cohort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static dev.snehil.cohort.Fixtures.PARSER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behaviour every repository implementation must have. Each implementation's test extends
 * this, so in-memory and Postgres are held to exactly the same contract (Liskov substitution).
 */
abstract class RepositoryContract {

    protected abstract CohortRepository cohorts();

    protected abstract MembershipRepository memberships();

    private static Cohort cohort(String id, int priority, String rule) {
        return new Cohort(id, "Name of " + id, priority, rule, PARSER.parse(rule), 1);
    }

    @Test
    void saveInsertsAtVersionOneAndUpdatesBumpTheVersion() {
        Cohort v1 = cohorts().save(cohort("quiet", 10, "orders_30d >= 4"));
        Cohort v2 = cohorts().save(cohort("quiet", 20, "orders_30d >= 5"));

        assertThat(v1.version()).isEqualTo(1);
        assertThat(v2.version()).isEqualTo(2);
        Cohort loaded = cohorts().findById("quiet").orElseThrow();
        assertThat(loaded.version()).isEqualTo(2);
        assertThat(loaded.priority()).isEqualTo(20);
        assertThat(loaded.ruleText()).isEqualTo("orders_30d >= 5");
        assertThat(loaded.rule()).isEqualTo(PARSER.parse("orders_30d >= 5"));
    }

    @Test
    void findAllIsOrderedByPriorityThenId() {
        cohorts().save(cohort("b-low", 1, "orders_30d = 1"));
        cohorts().save(cohort("high", 9, "orders_30d = 1"));
        cohorts().save(cohort("a-low", 1, "orders_30d = 1"));
        assertThat(cohorts().findAll()).extracting(Cohort::id).containsExactly("high", "a-low", "b-low");
    }

    @Test
    void concurrentSavesNeverLoseAVersion() throws Exception {
        cohorts().save(cohort("hot", 1, "orders_30d = 1"));
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 40)
                    .mapToObj(i -> pool.submit(() -> cohorts().save(cohort("hot", i, "orders_30d = 1"))))
                    .toList();
            for (var f : futures) f.get();
        }
        assertThat(cohorts().findById("hot").orElseThrow().version()).isEqualTo(41);
    }

    @Test
    void deleteRemovesTheCohortAndItsMemberships() {
        cohorts().save(cohort("gone", 1, "orders_30d = 1"));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        memberships().replace("u1", List.of(new Membership("u1", "gone", 1, now)));

        assertThat(cohorts().delete("gone")).isTrue();
        assertThat(cohorts().delete("gone")).isFalse();
        assertThat(cohorts().findById("gone")).isEmpty();
        assertThat(memberships().findByUser("u1")).isEmpty();
    }

    @Test
    void replaceSwapsAUsersMembershipsWholesale() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        memberships().replace("u1", List.of(new Membership("u1", "b", 1, now), new Membership("u1", "a", 3, now)));
        memberships().replace("u2", List.of(new Membership("u2", "a", 3, now)));
        assertThat(memberships().findByUser("u1")).extracting(Membership::cohortId).containsExactly("a", "b");
        assertThat(memberships().countByCohort("a")).isEqualTo(2);

        memberships().replace("u1", List.of(new Membership("u1", "c", 1, now)));
        assertThat(memberships().findByUser("u1")).containsExactly(new Membership("u1", "c", 1, now));
        assertThat(memberships().countByCohort("a")).isEqualTo(1);
        assertThat(memberships().findByUser("nobody")).isEmpty();
    }
}
