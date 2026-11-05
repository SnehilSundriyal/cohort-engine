package dev.snehil.cohort.rule;

import dev.snehil.cohort.domain.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static dev.snehil.cohort.Fixtures.CTX;
import static dev.snehil.cohort.Fixtures.PARSER;
import static dev.snehil.cohort.Fixtures.asha;
import static dev.snehil.cohort.Fixtures.newbie;
import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluationTest {

    @Test
    void missingAttributeNeverMatchesButNotOfItDoes() {
        // newbie has no days_since_last_order at all
        assertThat(PARSER.parse("days_since_last_order > 14").matches(newbie(), CTX)).isFalse();
        assertThat(PARSER.parse("days_since_last_order <= 14").matches(newbie(), CTX)).isFalse();
        assertThat(PARSER.parse("NOT days_since_last_order > 14").matches(newbie(), CTX)).isTrue();
    }

    @Test
    void traceExplainsEveryCondition() {
        Rule rule = PARSER.parse("orders_30d >= 4 AND days_since_last_order > 30");
        Trace t = rule.trace(asha(), CTX);

        assertThat(t.passed()).isFalse();
        assertThat(t.pretty()).isEqualTo("""
                ✗ AND
                  ✓ orders_30d >= 4   (actual: 6)
                  ✗ days_since_last_order > 30   (actual: 20)
                """);
        assertThat(t.failedLeaves()).extracting(Trace::label).containsExactly("days_since_last_order > 30");
    }

    @Test
    void traceAgreesWithMatchesOnEveryDefaultCohort() {
        var cohorts = dev.snehil.cohort.catalog.DefaultCohorts.load(PARSER);
        for (UserProfile p : new UserProfile[]{asha(), newbie()}) {
            for (var c : cohorts) {
                assertThat(c.rule().trace(p, CTX).passed())
                        .as(c.id() + " for " + p.userId())
                        .isEqualTo(c.rule().matches(p, CTX));
            }
        }
    }

    @Test
    void missingAttributeShowsInTrace() {
        Trace t = PARSER.parse("days_since_last_order > 14").trace(newbie(), CTX);
        assertThat(t.detail()).isEqualTo("missing");
    }

    @Test
    void andShortCircuits() {
        AtomicInteger calls = new AtomicInteger();
        Rule counting = countingRule(calls);
        Rule and = new AndRule(java.util.List.of(PARSER.parse("orders_30d = 0"), counting));
        assertThat(and.matches(asha(), CTX)).isFalse();
        assertThat(calls).hasValue(0); // second child never evaluated
    }

    @Test
    void orShortCircuits() {
        AtomicInteger calls = new AtomicInteger();
        Rule or = new OrRule(java.util.List.of(PARSER.parse("orders_30d = 6"), countingRule(calls)));
        assertThat(or.matches(asha(), CTX)).isTrue();
        assertThat(calls).hasValue(0);
    }

    /** A Condition whose operator counts how often it runs. */
    private static Rule countingRule(AtomicInteger calls) {
        var op = new dev.snehil.cohort.rule.op.Operator() {
            public String symbol() { return "COUNT"; }
            public Arity arity() { return Arity.ONE; }
            public java.util.Set<dev.snehil.cohort.domain.AttributeType> supportedTypes() {
                return java.util.EnumSet.allOf(dev.snehil.cohort.domain.AttributeType.class);
            }
            public boolean test(dev.snehil.cohort.domain.Value a, java.util.List<dev.snehil.cohort.domain.Value> o, EvalContext c) {
                calls.incrementAndGet();
                return true;
            }
        };
        return Condition.of("orders_30d", op, dev.snehil.cohort.domain.Value.num(0));
    }
}
