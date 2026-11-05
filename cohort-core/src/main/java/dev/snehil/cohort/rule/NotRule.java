package dev.snehil.cohort.rule;

import dev.snehil.cohort.domain.UserProfile;

import java.util.List;
import java.util.Objects;

/** Inverts one child. */
public record NotRule(Rule inner) implements Rule {
    public NotRule {
        Objects.requireNonNull(inner, "inner");
    }

    @Override
    public boolean matches(UserProfile profile, EvalContext ctx) {
        return !inner.matches(profile, ctx);
    }

    @Override
    public Trace trace(UserProfile profile, EvalContext ctx) {
        Trace child = inner.trace(profile, ctx);
        return new Trace("NOT", !child.passed(), null, List.of(child));
    }

    @Override
    public String render() {
        return inner instanceof Condition ? "NOT " + inner.render() : "NOT (" + inner.render() + ")";
    }
}
