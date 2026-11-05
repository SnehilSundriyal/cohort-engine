package dev.snehil.cohort.repo;

import java.util.List;

/** Storage for each user's latest cohort assignment. */
public interface MembershipRepository {

    /** Atomically replaces everything stored for {@code userId} with {@code memberships}. */
    void replace(String userId, List<Membership> memberships);

    /** The user's stored memberships, ordered by cohort id. Empty if never evaluated. */
    List<Membership> findByUser(String userId);

    long countByCohort(String cohortId);
}
