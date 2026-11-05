package dev.snehil.cohort.repo;

import dev.snehil.cohort.domain.Cohort;

import java.util.List;
import java.util.Optional;

/**
 * Storage for cohort definitions. The engine and the API depend on this interface, never on a
 * database (dependency inversion), so tests and local runs use {@link InMemoryCohortRepository}
 * and production uses {@link JdbcCohortRepository} with no code change above this line.
 */
public interface CohortRepository {

    /**
     * Inserts the cohort, or replaces the definition with the same id. Returns it with the stored
     * version: 1 on insert, previous + 1 on update. The version passed in is ignored.
     */
    Cohort save(Cohort cohort);

    Optional<Cohort> findById(String id);

    /** All cohorts, highest priority first. */
    List<Cohort> findAll();

    /** Removes the cohort and its stored memberships. Returns false if it didn't exist. */
    boolean delete(String id);
}
