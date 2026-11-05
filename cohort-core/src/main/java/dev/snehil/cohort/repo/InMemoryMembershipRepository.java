package dev.snehil.cohort.repo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMembershipRepository implements MembershipRepository {
    private final Map<String, List<Membership>> byUser = new ConcurrentHashMap<>();

    @Override
    public void replace(String userId, List<Membership> memberships) {
        for (Membership m : memberships) {
            if (!m.userId().equals(userId)) throw new IllegalArgumentException("membership for another user: " + m);
        }
        byUser.put(userId, memberships.stream().sorted(Comparator.comparing(Membership::cohortId)).toList());
    }

    @Override
    public List<Membership> findByUser(String userId) {
        return byUser.getOrDefault(userId, List.of());
    }

    @Override
    public long countByCohort(String cohortId) {
        return byUser.values().stream().flatMap(List::stream).filter(m -> m.cohortId().equals(cohortId)).count();
    }

    void deleteCohort(String cohortId) {
        byUser.replaceAll((user, list) -> list.stream().filter(m -> !m.cohortId().equals(cohortId)).toList());
    }
}
