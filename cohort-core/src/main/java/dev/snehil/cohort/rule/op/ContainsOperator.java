package dev.snehil.cohort.rule.op;

import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.rule.EvalContext;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code categories_30d CONTAINS 'dairy'}: set membership for a set attribute, or a
 * case-insensitive substring match for a text attribute.
 */
public final class ContainsOperator implements Operator {
    private static final Set<AttributeType> TYPES = Set.of(AttributeType.TEXT_SET, AttributeType.TEXT);

    @Override public String symbol() { return "CONTAINS"; }
    @Override public Arity arity() { return Arity.ONE; }
    @Override public Set<AttributeType> supportedTypes() { return TYPES; }

    @Override
    public AttributeType operandType(AttributeType attributeType) {
        return AttributeType.TEXT;
    }

    @Override
    public boolean test(Value actual, List<Value> operands, EvalContext ctx) {
        if (!(operands.getFirst() instanceof Value.Text needle)) {
            throw new Values.TypeMismatchException("CONTAINS needs a text operand");
        }
        return switch (actual) {
            case Value.TextSet set -> set.values().contains(needle.value());
            case Value.Text text -> text.value().toLowerCase(Locale.ROOT)
                    .contains(needle.value().toLowerCase(Locale.ROOT));
            default -> throw new Values.TypeMismatchException("CONTAINS can't apply to " + actual.type());
        };
    }

    @Override public String toString() { return symbol(); }
}
