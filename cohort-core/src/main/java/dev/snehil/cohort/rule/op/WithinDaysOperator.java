package dev.snehil.cohort.rule.op;

import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.rule.EvalContext;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * {@code first_order_date WITHIN_DAYS 14}: the date is no more than N days before today
 * (and not in the future).
 *
 * <p>Time-dependent: the answer can flip overnight with no change to the profile, so the
 * incremental evaluator re-checks cohorts using it whenever the date moves on.
 */
public final class WithinDaysOperator implements Operator {

    @Override public String symbol() { return "WITHIN_DAYS"; }
    @Override public Arity arity() { return Arity.ONE; }
    @Override public Set<AttributeType> supportedTypes() { return Set.of(AttributeType.DATE); }
    @Override public boolean timeDependent() { return true; }

    @Override
    public AttributeType operandType(AttributeType attributeType) {
        return AttributeType.NUMBER;
    }

    @Override
    public boolean test(Value actual, List<Value> operands, EvalContext ctx) {
        if (!(actual instanceof Value.Date date) || !(operands.getFirst() instanceof Value.Num days)) {
            throw new Values.TypeMismatchException("WITHIN_DAYS needs a date attribute and a number of days");
        }
        long age = ChronoUnit.DAYS.between(date.value(), ctx.today());
        return age >= 0 && age <= days.value();
    }

    @Override public String toString() { return symbol(); }
}
