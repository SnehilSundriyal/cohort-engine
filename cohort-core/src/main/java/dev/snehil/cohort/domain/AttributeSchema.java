package dev.snehil.cohort.domain;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The attributes a rule may refer to, and their types. The parser checks rules against it, so
 * {@code city > 5} or a misspelt {@code oders_30d} fail when the cohort is saved, not silently
 * at evaluation time.
 */
public record AttributeSchema(Map<String, AttributeType> types) {
    public AttributeSchema {
        types = Map.copyOf(types);
    }

    public Optional<AttributeType> typeOf(String attribute) {
        return Optional.ofNullable(types.get(attribute));
    }

    public Set<String> names() {
        return new TreeMap<>(types).keySet();
    }

    /** The closest known attribute name, for "did you mean" messages. */
    public Optional<String> closestTo(String unknown) {
        return types.keySet().stream()
                .map(name -> Map.entry(name, editDistance(unknown, name)))
                .filter(e -> e.getValue() <= Math.max(2, unknown.length() / 4))
                .min(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey);
    }

    /** Levenshtein distance, two-row dynamic programming. */
    static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitute = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(substitute, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }
}
