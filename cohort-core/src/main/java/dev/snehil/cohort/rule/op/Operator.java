package dev.snehil.cohort.rule.op;

import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.rule.EvalContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One comparison strategy (Strategy pattern). A {@link dev.snehil.cohort.rule.Condition} holds
 * an Operator and delegates to it, so adding an operator means adding a class and registering it
 * in {@link OperatorRegistry}. The condition, the evaluator and the parser don't change.
 */
public interface Operator {

    /** How it's written in rule text: {@code >=}, {@code IN}, {@code WITHIN_DAYS}. */
    String symbol();

    Arity arity();

    /** Attribute types this operator can be applied to. Checked when a rule is parsed. */
    Set<AttributeType> supportedTypes();

    /** Type each operand must have, given the attribute's type. Usually the same type. */
    default AttributeType operandType(AttributeType attributeType) {
        return attributeType;
    }

    boolean test(Value actual, List<Value> operands, EvalContext ctx);

    /** True if the answer depends on {@link EvalContext#today()}, not only on the profile. */
    default boolean timeDependent() {
        return false;
    }

    /** Renders "operator operands", the part after the attribute name. */
    default String render(List<Value> operands) {
        return switch (arity()) {
            case ONE -> symbol() + " " + operands.getFirst().render();
            case TWO -> symbol() + " " + operands.get(0).render() + " AND " + operands.get(1).render();
            case LIST -> symbol() + " " + operands.stream().map(Value::render)
                    .collect(Collectors.joining(", ", "(", ")"));
        };
    }

    enum Arity {
        ONE, TWO, LIST;

        public void check(String symbol, int count) {
            boolean ok = switch (this) {
                case ONE -> count == 1;
                case TWO -> count == 2;
                case LIST -> count >= 1;
            };
            if (!ok) throw new IllegalArgumentException(symbol + " can't take " + count + " operand(s)");
        }
    }
}
