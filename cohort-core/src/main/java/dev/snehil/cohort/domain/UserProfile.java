package dev.snehil.cohort.domain;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Everything the engine knows about one user: a map of typed, derived attributes.
 * Immutable. A "change" to a user is a new UserProfile, and {@link #changedAttributes}
 * says which attributes differ, which is what the incremental evaluator keys on.
 */
public record UserProfile(String userId, Map<String, Value> attributes) {
    public UserProfile {
        Objects.requireNonNull(userId, "userId");
        if (userId.isBlank()) throw new IllegalArgumentException("userId can't be blank");
        attributes = Map.copyOf(attributes);
    }

    public Optional<Value> get(String attribute) {
        return Optional.ofNullable(attributes.get(attribute));
    }

    /** Attributes whose value was added, removed or changed between {@code this} and {@code newer}. */
    public Set<String> changedAttributes(UserProfile newer) {
        Set<String> changed = new HashSet<>();
        for (var e : attributes.entrySet()) {
            if (!Objects.equals(e.getValue(), newer.attributes.get(e.getKey()))) changed.add(e.getKey());
        }
        for (String key : newer.attributes.keySet()) {
            if (!attributes.containsKey(key)) changed.add(key);
        }
        return changed;
    }

    @Override
    public String toString() {
        return "UserProfile[" + userId + ", " + new TreeMap<>(attributes) + "]";
    }
}
