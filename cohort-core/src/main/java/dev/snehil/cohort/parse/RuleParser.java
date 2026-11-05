package dev.snehil.cohort.parse;

import dev.snehil.cohort.domain.AttributeSchema;
import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.parse.Token.Type;
import dev.snehil.cohort.rule.AndRule;
import dev.snehil.cohort.rule.Condition;
import dev.snehil.cohort.rule.NotRule;
import dev.snehil.cohort.rule.OrRule;
import dev.snehil.cohort.rule.Rule;
import dev.snehil.cohort.rule.op.Operator;
import dev.snehil.cohort.rule.op.OperatorRegistry;
import dev.snehil.cohort.rule.op.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Recursive-descent parser for the rule language (the Interpreter pattern's front half).
 *
 * <pre>
 * rule      := or EOF
 * or        := and ( OR and )*
 * and       := not ( AND not )*
 * not       := NOT not | primary
 * primary   := '(' or ')' | condition
 * condition := IDENT operator operands
 * operands  := literal                      -- =, !=, >, >=, <, <=, CONTAINS, WITHIN_DAYS
 *            | literal AND literal          -- BETWEEN
 *            | '(' literal ( ',' literal )* ')'   -- IN
 * literal   := NUMBER | STRING | TRUE | FALSE
 * </pre>
 *
 * <p>Precedence comes from the grammar's layering: each level calls the next-tighter level, so
 * NOT binds tighter than AND, which binds tighter than OR, and {@code a OR b AND c} parses as
 * {@code a OR (b AND c)}. The {@code AND} inside {@code BETWEEN 1 AND 5} is consumed by the
 * condition rule before the and-level ever sees it, so it isn't mistaken for a logical AND.
 *
 * <p>If a schema is given, each condition is also checked: the attribute must exist, the operator
 * must support its type, and literals are coerced to the right type (e.g. {@code '2026-10-01'}
 * becomes a date). Every error carries the position of the token that caused it.
 */
public final class RuleParser {
    /** Guards against stack overflow on input like "((((((...". */
    static final int MAX_DEPTH = 64;

    private final OperatorRegistry operators;
    private final AttributeSchema schema; // null = no type checking

    public RuleParser(OperatorRegistry operators, AttributeSchema schema) {
        this.operators = operators;
        this.schema = schema;
    }

    public RuleParser(OperatorRegistry operators) {
        this(operators, null);
    }

    public Rule parse(String text) {
        if (text == null || text.isBlank()) throw new RuleParseException("rule is empty", text == null ? "" : text, 0);
        return new Run(text, new Lexer(text).tokenize()).parseRule();
    }

    /** Parser state for one input. A fresh Run per call keeps RuleParser itself thread-safe. */
    private final class Run {
        private final String input;
        private final List<Token> tokens;
        private int index = 0;
        private int depth = 0;

        Run(String input, List<Token> tokens) {
            this.input = input;
            this.tokens = tokens;
        }

        Rule parseRule() {
            Rule rule = parseOr();
            Token t = peek();
            if (t.type() != Type.EOF) {
                throw error(t.type() == Type.RPAREN ? "')' has no matching '('" : "expected AND, OR or end of rule, found " + t.describe(), t);
            }
            return rule;
        }

        private Rule parseOr() {
            List<Rule> parts = new ArrayList<>();
            parts.add(parseAnd());
            while (match(Type.OR)) parts.add(parseAnd());
            return parts.size() == 1 ? parts.getFirst() : new OrRule(flatten(parts, OrRule.class));
        }

        private Rule parseAnd() {
            List<Rule> parts = new ArrayList<>();
            parts.add(parseNot());
            while (match(Type.AND)) parts.add(parseNot());
            return parts.size() == 1 ? parts.getFirst() : new AndRule(flatten(parts, AndRule.class));
        }

        private Rule parseNot() {
            if (match(Type.NOT)) {
                enter(previous());
                try {
                    return new NotRule(parseNot());
                } finally {
                    depth--;
                }
            }
            return parsePrimary();
        }

        private Rule parsePrimary() {
            Token t = peek();
            if (match(Type.LPAREN)) {
                enter(t);
                try {
                    Rule inner = parseOr();
                    expect(Type.RPAREN, "expected ')' to close the '(' at position " + t.position());
                    return inner;
                } finally {
                    depth--;
                }
            }
            if (t.type() == Type.IDENT) return parseCondition();
            if (t.type() == Type.EOF) throw error("rule ends too early; expected a condition", t);
            throw error("expected a condition or '(', found " + t.describe(), t);
        }

        private Condition parseCondition() {
            Token attrToken = advance();
            String attribute = attrToken.text();

            AttributeType attrType = null;
            if (schema != null) {
                attrType = schema.typeOf(attribute).orElseThrow(() -> error(
                        "unknown attribute '" + attribute + "'"
                                + schema.closestTo(attribute).map(s -> " (did you mean '" + s + "'?)").orElse(""),
                        attrToken));
            }

            Token opToken = advance();
            Operator op = (opToken.type() == Type.SYMBOL_OP || opToken.type() == Type.IDENT)
                    ? operators.find(opToken.text()).orElse(null) : null;
            if (op == null) {
                throw error("expected an operator after '" + attribute + "', found " + opToken.describe(), opToken);
            }
            if (attrType != null && !op.supportedTypes().contains(attrType)) {
                throw error("'" + op.symbol() + "' can't be used on " + attrType + " attribute '" + attribute + "'", opToken);
            }

            AttributeType operandType = attrType == null ? null : op.operandType(attrType);
            List<Value> operands = switch (op.arity()) {
                case ONE -> List.of(literal(operandType));
                case TWO -> {
                    Value low = literal(operandType);
                    expect(Type.AND, op.symbol() + " needs AND between its two values");
                    yield List.of(low, literal(operandType));
                }
                case LIST -> {
                    expect(Type.LPAREN, op.symbol() + " needs a list like ('a', 'b')");
                    List<Value> list = new ArrayList<>();
                    do list.add(literal(operandType)); while (match(Type.COMMA));
                    expect(Type.RPAREN, "expected ',' or ')' in the " + op.symbol() + " list");
                    yield list;
                }
            };
            return new Condition(attribute, op, operands);
        }

        private Value literal(AttributeType expected) {
            Token t = advance();
            Value raw = switch (t.type()) {
                case NUMBER -> {
                    try {
                        yield Value.num(Long.parseLong(t.text()));
                    } catch (NumberFormatException e) {
                        throw error("number is too large", t);
                    }
                }
                case STRING -> Value.text(t.text());
                case TRUE -> Value.bool(true);
                case FALSE -> Value.bool(false);
                default -> throw error("expected a value (number, 'text', true or false), found " + t.describe(), t);
            };
            if (expected == null) return raw;
            Optional<Value> coerced = Values.coerce(raw, expected);
            if (coerced.isEmpty()) {
                String hint = expected == AttributeType.DATE ? " like '2026-10-01'" : "";
                throw error("expected a " + expected + hint + ", found " + t.describe(), t);
            }
            return coerced.get();
        }

        // ---- token helpers ----

        private Token peek() {
            return tokens.get(index);
        }

        private Token previous() {
            return tokens.get(index - 1);
        }

        private Token advance() {
            Token t = tokens.get(index);
            if (t.type() != Type.EOF) index++;
            return t;
        }

        private boolean match(Type type) {
            if (peek().type() != type) return false;
            index++;
            return true;
        }

        private void expect(Type type, String message) {
            Token t = peek();
            if (t.type() != type) throw error(message + ", found " + t.describe(), t);
            index++;
        }

        private void enter(Token at) {
            if (++depth > MAX_DEPTH) throw error("rule is nested too deeply (max " + MAX_DEPTH + ")", at);
        }

        private RuleParseException error(String reason, Token at) {
            return new RuleParseException(reason, input, at.position());
        }
    }

    /** {@code a AND (b AND c)} becomes one AND with three children: same meaning, flatter tree. */
    private static List<Rule> flatten(List<Rule> parts, Class<? extends Rule> kind) {
        List<Rule> out = new ArrayList<>();
        for (Rule r : parts) {
            if (kind == AndRule.class && r instanceof AndRule a) out.addAll(a.children());
            else if (kind == OrRule.class && r instanceof OrRule o) out.addAll(o.children());
            else out.add(r);
        }
        return out;
    }
}
