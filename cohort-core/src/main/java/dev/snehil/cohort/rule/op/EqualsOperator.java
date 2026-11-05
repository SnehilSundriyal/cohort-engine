package dev.snehil.cohort.rule.op;

import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.rule.EvalContext;

import java.util.List;
import java.util.Set;

/** {@code =} and, negated, {@code !=}. */
public final class EqualsOperator implements Operator {
    private static final Set<AttributeType> TYPES =
            Set.of(AttributeType.NUMBER, AttributeType.BOOLEAN, AttributeType.TEXT, AttributeType.DATE);

    private final boolean negated;

    private EqualsOperator(boolean negated) {
        this.negated = negated;
    }

    public static EqualsOperator equalTo() { return new EqualsOperator(false); }
    public static EqualsOperator notEqualTo() { return new EqualsOperator(true); }

    @Override public String symbol() { return negated ? "!=" : "="; }
    @Override public Arity arity() { return Arity.ONE; }
    @Override public Set<AttributeType> supportedTypes() { return TYPES; }

    @Override
    public boolean test(Value actual, List<Value> operands, EvalContext ctx) {
        return Values.equal(actual, operands.getFirst()) != negated;
    }

    @Override public String toString() { return symbol(); }
}
