package dev.snehil.cohort.rule;

import dev.snehil.cohort.domain.UserProfile;

import java.util.List;
import java.util.stream.Collectors;

/** Matches when any child matches. */
public record OrRule(List<Rule> children) implements Rule {
    public OrRule {
        children = List.copyOf(children);
        if (children.size() < 2) throw new IllegalArgumentException("OR needs at least two children");
    }

    public static OrRule of(Rule... children) {
        return new OrRule(List.of(children));
    }

    @Override
    public boolean matches(UserProfile profile, EvalContext ctx) {
        for (Rule child : children) {
            if (child.matches(profile, ctx)) return true; // short-circuit on the first hit
        }
        return false;
    }

    @Override
    public Trace trace(UserProfile profile, EvalContext ctx) {
        List<Trace> traces = children.stream().map(c -> c.trace(profile, ctx)).toList();
        return new Trace("OR", traces.stream().anyMatch(Trace::passed), null, traces);
    }

    @Override
    public String render() {
        // Children of OR never need parentheses: AND and NOT already bind tighter.
        return children.stream().map(Rule::render).collect(Collectors.joining(" OR "));
    }
}
