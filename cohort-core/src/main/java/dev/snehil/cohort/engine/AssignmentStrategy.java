package dev.snehil.cohort.engine;

import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.rule.EvalContext;

import java.util.Comparator;
import java.util.List;

/**
 * How a user is assigned to cohorts (Strategy pattern). Marketing usually wants every matching
 * cohort; a lifecycle email flow wants exactly one, so it doesn't send "welcome back" and "we
 * miss you" on the same day.
 */
public sealed interface AssignmentStrategy permits AssignmentStrategy.AllMatching, AssignmentStrategy.HighestPriority {

    /** Highest priority first; ties broken by id so the order is stable. */
    Comparator<Cohort> PRIORITY_ORDER = Comparator.comparingInt(Cohort::priority).reversed()
            .thenComparing(Cohort::id);

    /** Returns the cohorts the user is assigned to, highest priority first. */
    List<Cohort> assign(UserProfile profile, List<Cohort> cohortsByPriority, EvalContext ctx);

    String name();

    static AssignmentStrategy fromName(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "all", "all-matching" -> new AllMatching();
            case "priority", "highest-priority", "one" -> new HighestPriority();
            default -> throw new IllegalArgumentException("unknown assignment mode: " + name + " (use 'all' or 'priority')");
        };
    }

    /** Every cohort whose rule matches. */
    record AllMatching() implements AssignmentStrategy {
        public List<Cohort> assign(UserProfile profile, List<Cohort> cohortsByPriority, EvalContext ctx) {
            return cohortsByPriority.stream().filter(c -> c.rule().matches(profile, ctx)).toList();
        }

        public String name() { return "all"; }
    }

    /**
     * At most one cohort: the highest-priority match. Walks cohorts in priority order and stops at
     * the first hit, so it usually evaluates far fewer rules than {@link AllMatching}.
     */
    record HighestPriority() implements AssignmentStrategy {
        public List<Cohort> assign(UserProfile profile, List<Cohort> cohortsByPriority, EvalContext ctx) {
            for (Cohort c : cohortsByPriority) {
                if (c.rule().matches(profile, ctx)) return List.of(c);
            }
            return List.of();
        }

        public String name() { return "priority"; }
    }
}
