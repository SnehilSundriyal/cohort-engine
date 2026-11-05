package dev.snehil.cohort.rule;

import java.util.HashSet;
import java.util.Set;

/**
 * Tree walks written as exhaustive pattern-matching switches over the sealed {@link Rule}.
 *
 * <p>This is the other way to add behaviour to a closed hierarchy (instead of a method on every
 * node): no default branch, so if a fifth rule kind is ever added the compiler flags every
 * switch here until it's handled.
 */
public final class Rules {
    private Rules() {}

    public static Set<String> attributes(Rule rule) {
        Set<String> out = new HashSet<>();
        collectAttributes(rule, out);
        return Set.copyOf(out);
    }

    private static void collectAttributes(Rule rule, Set<String> out) {
        switch (rule) {
            case Condition c -> out.add(c.attribute());
            case NotRule n -> collectAttributes(n.inner(), out);
            case AndRule a -> a.children().forEach(child -> collectAttributes(child, out));
            case OrRule o -> o.children().forEach(child -> collectAttributes(child, out));
        }
    }

    public static boolean timeDependent(Rule rule) {
        return switch (rule) {
            case Condition c -> c.operator().timeDependent();
            case NotRule n -> timeDependent(n.inner());
            case AndRule a -> a.children().stream().anyMatch(Rules::timeDependent);
            case OrRule o -> o.children().stream().anyMatch(Rules::timeDependent);
        };
    }

    public static int conditionCount(Rule rule) {
        return switch (rule) {
            case Condition c -> 1;
            case NotRule n -> conditionCount(n.inner());
            case AndRule a -> a.children().stream().mapToInt(Rules::conditionCount).sum();
            case OrRule o -> o.children().stream().mapToInt(Rules::conditionCount).sum();
        };
    }

    public static int depth(Rule rule) {
        return switch (rule) {
            case Condition c -> 1;
            case NotRule n -> 1 + depth(n.inner());
            case AndRule a -> 1 + a.children().stream().mapToInt(Rules::depth).max().orElse(0);
            case OrRule o -> 1 + o.children().stream().mapToInt(Rules::depth).max().orElse(0);
        };
    }
}
