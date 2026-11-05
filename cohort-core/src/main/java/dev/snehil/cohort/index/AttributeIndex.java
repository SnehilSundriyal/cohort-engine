package dev.snehil.cohort.index;

import dev.snehil.cohort.domain.Cohort;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inverted index from attribute name to the cohorts whose rules read it.
 *
 * <p>When {@code city} changes, only cohorts that mention {@code city} can change their answer,
 * so only those need re-evaluating. Built once from the rule trees via {@code Rule.attributes()}.
 */
public final class AttributeIndex {
    private final Map<String, List<Cohort>> byAttribute;
    private final List<Cohort> timeDependent;
    private final List<Cohort> all;

    public AttributeIndex(Collection<Cohort> cohorts) {
        Map<String, List<Cohort>> m = new HashMap<>();
        for (Cohort c : cohorts) {
            for (String attribute : c.rule().attributes()) {
                m.computeIfAbsent(attribute, k -> new java.util.ArrayList<>()).add(c);
            }
        }
        m.replaceAll((k, v) -> List.copyOf(v));
        this.byAttribute = Map.copyOf(m);
        this.timeDependent = cohorts.stream().filter(c -> c.rule().timeDependent()).toList();
        this.all = List.copyOf(cohorts);
    }

    /** Cohorts that read at least one of the given attributes, without duplicates. */
    public Set<Cohort> cohortsReading(Collection<String> attributes) {
        Set<Cohort> out = new LinkedHashSet<>();
        for (String a : attributes) out.addAll(byAttribute.getOrDefault(a, List.of()));
        return out;
    }

    /** Cohorts whose answer can change just because the date moved on. */
    public List<Cohort> timeDependent() {
        return timeDependent;
    }

    public List<Cohort> all() {
        return all;
    }

    public int size() {
        return all.size();
    }
}
