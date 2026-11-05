package dev.snehil.cohort.repo;

import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.engine.AssignmentStrategy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCohortRepository implements CohortRepository {
    private final Map<String, Cohort> byId = new ConcurrentHashMap<>();
    private final MembershipRepository memberships;

    public InMemoryCohortRepository(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    public Cohort save(Cohort cohort) {
        // compute() runs atomically per key, so two concurrent saves can't both become version 2.
        return byId.compute(cohort.id(), (id, existing) ->
                cohort.withVersion(existing == null ? 1 : existing.version() + 1));
    }

    @Override
    public Optional<Cohort> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<Cohort> findAll() {
        return byId.values().stream().sorted(AssignmentStrategy.PRIORITY_ORDER).toList();
    }

    @Override
    public boolean delete(String id) {
        boolean removed = byId.remove(id) != null;
        if (removed && memberships instanceof InMemoryMembershipRepository m) m.deleteCohort(id);
        return removed;
    }
}
