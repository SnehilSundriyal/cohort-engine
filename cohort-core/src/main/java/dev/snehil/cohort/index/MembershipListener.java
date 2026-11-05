package dev.snehil.cohort.index;

/**
 * Observer of membership changes: e.g. trigger a win-back push when a user joins
 * {@code at-risk-regular}. The evaluator doesn't know who's listening.
 */
@FunctionalInterface
public interface MembershipListener {
    void onChange(MembershipEvent event);
}
