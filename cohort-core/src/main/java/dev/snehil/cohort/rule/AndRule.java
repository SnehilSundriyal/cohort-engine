package dev.snehil.cohort.rule;

import dev.snehil.cohort.domain.UserProfile;

import java.util.List;
import java.util.stream.Collectors;

/** Matches when every child matches. An AndRule is a Rule and has Rules: the Composite pattern. */
public record AndRule(List<Rule> children) implements Rule {
    public AndRule {
        children = List.copyOf(children);
        if (children.size() < 2) throw new IllegalArgumentException("AND needs at least two children");
    }

    public static AndRule of(Rule... children) {
        return new AndRule(List.of(children));
    }

    @Override
    public boolean matches(UserProfile profile, EvalContext ctx) {
        for (Rule child : children) {
            if (!child.matches(profile, ctx)) return false; // short-circuit on the first miss
        }
        return true;
    }

    @Override
    public Trace trace(UserProfile profile, EvalContext ctx) {
        List<Trace> traces = children.stream().map(c -> c.trace(profile, ctx)).toList();
        return new Trace("AND", traces.stream().allMatch(Trace::passed), null, traces);
    }

    @Override
    public String render() {
        // OR binds looser than AND, so an OR child needs parentheses to keep its meaning.
        return children.stream()
                .map(c -> c instanceof OrRule ? "(" + c.render() + ")" : c.render())
                .collect(Collectors.joining(" AND "));
    }
}
