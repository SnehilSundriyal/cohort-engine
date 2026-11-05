package dev.snehil.cohort.parse;

import dev.snehil.cohort.rule.AndRule;
import dev.snehil.cohort.rule.Condition;
import dev.snehil.cohort.rule.NotRule;
import dev.snehil.cohort.rule.OrRule;
import dev.snehil.cohort.rule.Rule;
import dev.snehil.cohort.rule.Rules;
import dev.snehil.cohort.rule.op.OperatorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static dev.snehil.cohort.Fixtures.PARSER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

class RuleParserTest {

    @Test
    void andBindsTighterThanOr() {
        Rule r = PARSER.parse("orders_30d = 1 OR orders_30d = 2 AND city = 'pune'");
        // a OR (b AND c)
        assertThat(r).isInstanceOf(OrRule.class);
        OrRule or = (OrRule) r;
        assertThat(or.children()).hasSize(2);
        assertThat(or.children().get(0)).isInstanceOf(Condition.class);
        assertThat(or.children().get(1)).isInstanceOf(AndRule.class);
    }

    @Test
    void notBindsTighterThanAnd() {
        Rule r = PARSER.parse("NOT orders_30d = 0 AND city = 'pune'");
        assertThat(r).asInstanceOf(type(AndRule.class))
                .satisfies(a -> assertThat(a.children().getFirst()).isInstanceOf(NotRule.class));
    }

    @Test
    void parenthesesOverridePrecedence() {
        Rule r = PARSER.parse("(orders_30d = 1 OR orders_30d = 2) AND city = 'pune'");
        assertThat(r).asInstanceOf(type(AndRule.class))
                .satisfies(a -> assertThat(a.children().getFirst()).isInstanceOf(OrRule.class));
    }

    @Test
    void betweensAndIsNotALogicalAnd() {
        Rule r = PARSER.parse("days_since_last_order BETWEEN 15 AND 60 AND orders_90d >= 6");
        assertThat(r).asInstanceOf(type(AndRule.class))
                .satisfies(a -> assertThat(a.children()).hasSize(2));
    }

    @Test
    void chainsAreFlattened() {
        Rule r = PARSER.parse("orders_30d = 1 AND (orders_7d = 1 AND city = 'pune') AND orders_total = 1");
        assertThat(r).asInstanceOf(type(AndRule.class))
                .satisfies(a -> assertThat(a.children()).hasSize(4));
    }

    @Test
    void keywordsAreCaseInsensitive() {
        assertThat(PARSER.parse("orders_30d >= 4 and not city in ('pune')"))
                .isEqualTo(PARSER.parse("orders_30d >= 4 AND NOT city IN ('pune')"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "orders_30d >= 4",
            "orders_30d >= 4 AND days_since_last_order > 14",
            "(orders_30d = 0 OR days_since_last_order > 60) AND city IN ('bengaluru', 'hyderabad')",
            "NOT (bought_premium = true OR categories_30d CONTAINS 'meat')",
            "NOT NOT orders_7d = 0",
            "first_order_date BETWEEN '2026-01-01' AND '2026-03-31' OR last_order_date WITHIN_DAYS 7",
            "city = 'it\\'s'",
    })
    void renderThenParseGivesTheSameTree(String text) {
        Rule once = PARSER.parse(text);
        Rule twice = PARSER.parse(once.render());
        assertThat(twice).isEqualTo(once);
        assertThat(twice.render()).isEqualTo(once.render());
    }

    static Stream<Arguments> badRules() {
        // rule, position of the caret, fragment of the message
        return Stream.of(
                Arguments.of("", 0, "empty"),
                Arguments.of("orders_30d >=", 13, "expected a value"),
                Arguments.of("orders_30d >= 4 AND", 19, "ends too early"),
                Arguments.of("oders_30d > 4", 0, "did you mean 'orders_30d'"),
                Arguments.of("orders_30d ~ 4", 11, "unexpected character"),
                Arguments.of("orders_30d LIKE 4", 11, "expected an operator"),
                Arguments.of("city > 4", 5, "can't be used on TEXT"),
                Arguments.of("orders_30d > 'four'", 13, "expected a NUMBER"),
                Arguments.of("first_order_date > 'soon'", 19, "like '2026-10-01'"),
                Arguments.of("(orders_30d > 4", 15, "to close the '(' at position 0"),
                Arguments.of("orders_30d > 4)", 14, "no matching '('"),
                Arguments.of("orders_30d BETWEEN 1 OR 5", 21, "needs AND"),
                Arguments.of("city IN 'pune'", 8, "needs a list"),
                Arguments.of("city = 'pune", 7, "never closed"),
                Arguments.of("orders_30d > 4 orders_7d > 1", 15, "expected AND, OR"),
                Arguments.of("orders_30d > 99999999999999999999", 13, "too large"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("badRules")
    void errorsPointAtTheProblem(String rule, int position, String message) {
        assertThatThrownBy(() -> PARSER.parse(rule))
                .isInstanceOfSatisfying(RuleParseException.class, e -> {
                    assertThat(e.position()).isEqualTo(position);
                    assertThat(e.reason()).contains(message);
                });
    }

    @Test
    void pointerDrawsACaret() {
        var e = org.junit.jupiter.api.Assertions.assertThrows(RuleParseException.class,
                () -> PARSER.parse("orders_30d >= 4 AND days_since_last_ordr > 14"));
        assertThat(e.pointer()).isEqualTo("""
                orders_30d >= 4 AND days_since_last_ordr > 14
                                    ^ unknown attribute 'days_since_last_ordr' (did you mean 'days_since_last_order'?)""");
    }

    @Test
    void deepNestingIsRejectedNotAStackOverflow() {
        String deep = "(".repeat(500) + "orders_30d > 1" + ")".repeat(500);
        assertThatThrownBy(() -> PARSER.parse(deep))
                .isInstanceOf(RuleParseException.class)
                .hasMessageContaining("nested too deeply");
    }

    @Test
    void nestingUpToTheLimitIsFine() {
        String ok = "(".repeat(RuleParser.MAX_DEPTH) + "orders_30d > 1" + ")".repeat(RuleParser.MAX_DEPTH);
        assertThat(Rules.conditionCount(PARSER.parse(ok))).isEqualTo(1);
    }

    @Test
    void withoutASchemaAnyAttributeIsAccepted() {
        var loose = new RuleParser(OperatorRegistry.defaults());
        assertThat(loose.parse("made_up_thing > 3").attributes()).containsExactly("made_up_thing");
    }

    @Test
    void attributesAndTimeDependence() {
        Rule r = PARSER.parse("orders_30d > 1 AND (city = 'pune' OR NOT first_order_date WITHIN_DAYS 7)");
        assertThat(r.attributes()).containsExactlyInAnyOrder("orders_30d", "city", "first_order_date");
        assertThat(r.timeDependent()).isTrue();
        assertThat(Rules.depth(r)).isEqualTo(4);
        assertThat(PARSER.parse("orders_30d > 1").timeDependent()).isFalse();
    }
}
