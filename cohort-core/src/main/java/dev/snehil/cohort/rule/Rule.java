package dev.snehil.cohort.rule;

import dev.snehil.cohort.domain.UserProfile;

import java.util.Set;

/**
 * A node in a rule tree (Composite pattern).
 *
 * <p>Sealed: the only rule kinds are {@link AndRule}, {@link OrRule}, {@link NotRule} and
 * {@link Condition}. New behaviour comes from new {@link dev.snehil.cohort.rule.op.Operator}s,
 * not new node kinds, so the tree stays closed while the operators stay open. Because the set
 * is closed, a {@code switch} over a Rule (see {@link Rules}) needs no default branch and the
 * compiler proves it handles every case.
 */
public sealed interface Rule permits AndRule, OrRule, NotRule, Condition {

    /** Fast path: true or false, short-circuiting. */
    boolean matches(UserProfile profile, EvalContext ctx);

    /** Slow path: evaluates every node and records why each one passed or failed. */
    Trace trace(UserProfile profile, EvalContext ctx);

    /** Canonical rule text. {@code parse(render(r))} gives back an equal tree. */
    String render();

    /** Every attribute this rule reads, used to build the attribute → cohort index. */
    default Set<String> attributes() {
        return Rules.attributes(this);
    }

    /** True if the result can change just because the date changed (e.g. WITHIN_DAYS). */
    default boolean timeDependent() {
        return Rules.timeDependent(this);
    }
}
