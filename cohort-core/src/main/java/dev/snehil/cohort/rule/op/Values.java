package dev.snehil.cohort.rule.op;

import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Value;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Comparison and coercion helpers shared by the operators. */
public final class Values {
    private Values() {}

    /**
     * Orders two values of the same kind. Dates are written as text in rules ({@code '2026-10-01'}),
     * so a text operand against a date attribute is coerced here as a fallback; normally the
     * parser has already coerced it using the schema.
     */
    public static int compare(Value actual, Value operand) {
        return switch (actual) {
            case Value.Num a when operand instanceof Value.Num b -> Long.compare(a.value(), b.value());
            case Value.Date a -> a.value().compareTo(asDate(operand));
            case Value.Text a when operand instanceof Value.Text b -> a.value().compareTo(b.value());
            default -> throw new TypeMismatchException(
                    "can't compare " + actual.type() + " with " + operand.type());
        };
    }

    public static boolean equal(Value actual, Value operand) {
        if (actual instanceof Value.Date a) return a.value().equals(asDate(operand));
        return actual.equals(operand);
    }

    private static LocalDate asDate(Value v) {
        return switch (v) {
            case Value.Date d -> d.value();
            case Value.Text t -> LocalDate.parse(t.value());
            default -> throw new TypeMismatchException("expected a date, got " + v.type());
        };
    }

    /** Converts a literal to the attribute's type, or explains why it can't. */
    public static Optional<Value> coerce(Value literal, AttributeType target) {
        if (literal.type() == target) return Optional.of(literal);
        if (target == AttributeType.DATE && literal instanceof Value.Text t) {
            try {
                return Optional.of(Value.date(LocalDate.parse(t.value())));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static final class TypeMismatchException extends RuntimeException {
        public TypeMismatchException(String message) {
            super(message);
        }
    }
}
