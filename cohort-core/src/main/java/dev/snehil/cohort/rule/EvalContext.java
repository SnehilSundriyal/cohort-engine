package dev.snehil.cohort.rule;

import java.time.LocalDate;
import java.util.Objects;

/**
 * What a rule may know besides the profile. Passing "today" in, instead of calling
 * {@code LocalDate.now()} inside operators, keeps evaluation deterministic and testable.
 */
public record EvalContext(LocalDate today) {
    public EvalContext {
        Objects.requireNonNull(today, "today");
    }

    public static EvalContext on(LocalDate today) {
        return new EvalContext(today);
    }
}
