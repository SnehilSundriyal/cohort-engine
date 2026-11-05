package dev.snehil.cohort.engine;

import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.rule.EvalContext;
import dev.snehil.cohort.rule.Trace;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The entry point for evaluating one user. Holds an immutable, priority-sorted snapshot of the
 * cohorts; updating the catalog means building a new engine (cheap), which keeps evaluation
 * lock-free and safe to share across threads.
 */
public final class CohortEngine {
    private final List<Cohort> cohorts; // highest priority first

    public CohortEngine(List<Cohort> cohorts) {
        this.cohorts = cohorts.stream().sorted(AssignmentStrategy.PRIORITY_ORDER).toList();
    }

    public List<Cohort> cohorts() {
        return cohorts;
    }

    public Optional<Cohort> cohort(String id) {
        return cohorts.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** Assigns the user and explains every cohort it was assigned to. */
    public Evaluation evaluate(UserProfile profile, AssignmentStrategy strategy, EvalContext ctx) {
        Objects.requireNonNull(profile, "profile");
        List<Match> matches = strategy.assign(profile, cohorts, ctx).stream()
                .map(c -> new Match(c, c.rule().trace(profile, ctx)))
                .toList();
        return new Evaluation(profile.userId(), strategy.name(), ctx, matches);
    }

    /** Why is (or isn't) this user in this cohort? */
    public Trace explain(UserProfile profile, String cohortId, EvalContext ctx) {
        Cohort c = cohort(cohortId).orElseThrow(() -> new IllegalArgumentException("no such cohort: " + cohortId));
        return c.rule().trace(profile, ctx);
    }

    public record Match(Cohort cohort, Trace trace) {}

    public record Evaluation(String userId, String mode, EvalContext ctx, List<Match> matches) {
        public List<String> cohortIds() {
            return matches.stream().map(m -> m.cohort().id()).toList();
        }
    }
}
