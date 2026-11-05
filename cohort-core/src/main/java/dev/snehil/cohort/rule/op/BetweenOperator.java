package dev.snehil.cohort.rule.op;

import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.rule.EvalContext;

import java.util.List;
import java.util.Set;

/** {@code days_since_last_order BETWEEN 15 AND 60}: inclusive on both ends, like SQL. */
public final class BetweenOperator implements Operator {
    private static final Set<AttributeType> TYPES = Set.of(AttributeType.NUMBER, AttributeType.DATE);

    @Override public String symbol() { return "BETWEEN"; }
    @Override public Arity arity() { return Arity.TWO; }
    @Override public Set<AttributeType> supportedTypes() { return TYPES; }

    @Override
    public boolean test(Value actual, List<Value> operands, EvalContext ctx) {
        return Values.compare(actual, operands.get(0)) >= 0 && Values.compare(actual, operands.get(1)) <= 0;
    }

    @Override public String toString() { return symbol(); }
}
