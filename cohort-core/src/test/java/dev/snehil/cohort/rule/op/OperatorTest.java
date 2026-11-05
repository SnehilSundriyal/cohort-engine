package dev.snehil.cohort.rule.op;

import dev.snehil.cohort.domain.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static dev.snehil.cohort.Fixtures.CTX;
import static dev.snehil.cohort.Fixtures.PARSER;
import static dev.snehil.cohort.Fixtures.TODAY;
import static dev.snehil.cohort.Fixtures.asha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every operator, through the parser, against one known profile (asha: 6 orders in 30d, quiet 20 days). */
class OperatorTest {

    @ParameterizedTest(name = "{0}  ->  {1}")
    @CsvSource(delimiter = '|', textBlock = """
            orders_30d = 6                                   | true
            orders_30d = 5                                   | false
            orders_30d != 5                                  | true
            orders_30d > 5                                   | true
            orders_30d > 6                                   | false
            orders_30d >= 6                                  | true
            orders_30d < 6                                   | false
            orders_30d <= 6                                  | true
            days_since_last_order BETWEEN 15 AND 60          | true
            days_since_last_order BETWEEN 20 AND 20          | true
            days_since_last_order BETWEEN 21 AND 60          | false
            city IN ('hyderabad', 'bengaluru')               | true
            city IN ('pune')                                 | false
            orders_30d IN (1, 2, 6)                          | true
            categories_30d CONTAINS 'dairy'                  | true
            categories_30d CONTAINS 'meat'                   | false
            city CONTAINS 'BENGAL'                           | true
            bought_premium = false                           | true
            bought_premium = true                            | false
            last_order_date WITHIN_DAYS 20                   | true
            last_order_date WITHIN_DAYS 19                   | false
            first_order_date < '2026-04-01'                  | true
            first_order_date = '2026-03-04'                  | true
            first_order_date BETWEEN '2026-03-01' AND '2026-03-31' | true
            first_order_week = '2026-W10'                    | true
            """)
    void operatorsAgainstAKnownProfile(String rule, boolean expected) {
        assertThat(PARSER.parse(rule).matches(asha(), CTX)).isEqualTo(expected);
    }

    @Test
    void withinDaysRejectsFutureDates() {
        var op = new WithinDaysOperator();
        assertThat(op.test(Value.date(TODAY.plusDays(1)), List.of(Value.num(30)), CTX)).isFalse();
        assertThat(op.test(Value.date(TODAY), List.of(Value.num(0)), CTX)).isTrue();
    }

    @Test
    void comparingDifferentTypesFailsLoudly() {
        assertThatThrownBy(() -> Values.compare(Value.num(1), Value.text("1")))
                .isInstanceOf(Values.TypeMismatchException.class);
    }

    @Test
    void textDateOperandIsCoercedAtRuntimeWithoutASchema() {
        assertThat(Values.compare(Value.date(LocalDate.of(2026, 1, 2)), Value.text("2026-01-01"))).isPositive();
    }

    @Test
    void registryIsOpenForNewOperators() {
        Operator startsWith = new Operator() {
            public String symbol() { return "STARTS_WITH"; }
            public Arity arity() { return Arity.ONE; }
            public java.util.Set<dev.snehil.cohort.domain.AttributeType> supportedTypes() {
                return java.util.Set.of(dev.snehil.cohort.domain.AttributeType.TEXT);
            }
            public boolean test(Value actual, List<Value> operands, dev.snehil.cohort.rule.EvalContext ctx) {
                return ((Value.Text) actual).value().startsWith(((Value.Text) operands.getFirst()).value());
            }
        };
        var parser = new dev.snehil.cohort.parse.RuleParser(OperatorRegistry.defaults().with(startsWith),
                dev.snehil.cohort.stats.OrderStatsCalculator.SCHEMA);

        // No change to the lexer, parser, Condition or engine was needed.
        assertThat(parser.parse("city STARTS_WITH 'beng'").matches(asha(), CTX)).isTrue();
    }

    @Test
    void registryRejectsDuplicateSymbols() {
        assertThatThrownBy(() -> OperatorRegistry.defaults().with(new InOperator()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
