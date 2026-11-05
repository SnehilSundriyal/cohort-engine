package dev.snehil.cohort.rule.op;

import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.rule.EvalContext;

import java.util.List;
import java.util.Set;

/** {@code city IN ('bengaluru', 'hyderabad')}: equal to any operand. */
public final class InOperator implements Operator {
    private static final Set<AttributeType> TYPES = Set.of(AttributeType.NUMBER, AttributeType.TEXT);

    @Override public String symbol() { return "IN"; }
    @Override public Arity arity() { return Arity.LIST; }
    @Override public Set<AttributeType> supportedTypes() { return TYPES; }

    @Override
    public boolean test(Value actual, List<Value> operands, EvalContext ctx) {
        for (Value operand : operands) {
            if (Values.equal(actual, operand)) return true;
        }
        return false;
    }

    @Override public String toString() { return symbol(); }
}
