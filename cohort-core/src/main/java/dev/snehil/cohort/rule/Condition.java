package dev.snehil.cohort.rule;

import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.rule.op.Operator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A leaf: {@code attribute operator operands}, e.g. {@code orders_30d >= 4} or
 * {@code days_since_last_order BETWEEN 15 AND 60}.
 *
 * <p>The comparison itself is delegated to an {@link Operator} (Strategy pattern), so
 * Condition never grows a switch over operator kinds.
 *
 * <p>A missing attribute never matches. A user with no orders has no
 * {@code days_since_last_order}, and "> 14" shouldn't be true for them.
 */
public record Condition(String attribute, Operator operator, List<Value> operands) implements Rule {
    public Condition {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(operator, "operator");
        operands = List.copyOf(operands);
        operator.arity().check(operator.symbol(), operands.size());
    }

    public static Condition of(String attribute, Operator operator, Value... operands) {
        return new Condition(attribute, operator, List.of(operands));
    }

    @Override
    public boolean matches(UserProfile profile, EvalContext ctx) {
        Optional<Value> actual = profile.get(attribute);
        return actual.isPresent() && operator.test(actual.get(), operands, ctx);
    }

    @Override
    public Trace trace(UserProfile profile, EvalContext ctx) {
        Optional<Value> actual = profile.get(attribute);
        if (actual.isEmpty()) return Trace.leaf(render(), false, "missing");
        boolean passed = operator.test(actual.get(), operands, ctx);
        return Trace.leaf(render(), passed, "actual: " + actual.get().render());
    }

    @Override
    public String render() {
        return attribute + " " + operator.render(operands);
    }
}
