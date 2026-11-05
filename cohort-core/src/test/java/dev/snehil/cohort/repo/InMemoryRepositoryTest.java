package dev.snehil.cohort.repo;

import org.junit.jupiter.api.BeforeEach;

class InMemoryRepositoryTest extends RepositoryContract {
    private InMemoryMembershipRepository memberships;
    private InMemoryCohortRepository cohorts;

    @BeforeEach
    void setUp() {
        memberships = new InMemoryMembershipRepository();
        cohorts = new InMemoryCohortRepository(memberships);
    }

    @Override protected CohortRepository cohorts() { return cohorts; }
    @Override protected MembershipRepository memberships() { return memberships; }
}
