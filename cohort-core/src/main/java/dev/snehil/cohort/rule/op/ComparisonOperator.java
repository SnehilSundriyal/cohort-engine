package dev.snehil.cohort.rule.op;

import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.rule.EvalContext;

import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Shared base for {@code > >= < <=} (Template Method: the base class does the comparing,
 * each subclass supplies only "what does compare() have to return?").
 *
 * <p>This is the one place in the codebase that uses class inheritance. It fits here because the
 * four operators really are the same thing with one difference. Everywhere else the code prefers
 * composition: rules contain rules, conditions contain operators.
 */
public abstract class ComparisonOperator implements Operator {
    private static final Set<AttributeType> TYPES = Set.of(AttributeType.NUMBER, AttributeType.DATE);

    private final String symbol;
    private final IntPredicate acceptsComparison;

    protected ComparisonOperator(String symbol, IntPredicate acceptsComparison) {
        this.symbol = symbol;
        this.acceptsComparison = acceptsComparison;
    }

    @Override
    public final String symbol() {
        return symbol;
    }

    @Override
    public final Arity arity() {
        return Arity.ONE;
    }

    @Override
    public Set<AttributeType> supportedTypes() {
        return TYPES;
    }

    @Override
    public final boolean test(Value actual, List<Value> operands, EvalContext ctx) {
        return acceptsComparison.test(Values.compare(actual, operands.getFirst()));
    }

    @Override
    public String toString() {
        return symbol;
    }

    public static final class GreaterThan extends ComparisonOperator {
        public GreaterThan() { super(">", c -> c > 0); }
    }

    public static final class GreaterOrEqual extends ComparisonOperator {
        public GreaterOrEqual() { super(">=", c -> c >= 0); }
    }

    public static final class LessThan extends ComparisonOperator {
        public LessThan() { super("<", c -> c < 0); }
    }

    public static final class LessOrEqual extends ComparisonOperator {
        public LessOrEqual() { super("<=", c -> c <= 0); }
    }
}
