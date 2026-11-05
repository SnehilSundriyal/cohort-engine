package dev.snehil.cohort.parse;

/**
 * One lexical token.
 *
 * @param position 0-based character offset in the rule text, carried so every error can point
 *                 at the exact spot
 */
public record Token(Type type, String text, int position) {

    public enum Type {
        IDENT,      // orders_30d, IN, BETWEEN, WITHIN_DAYS (keyword operators are looked up by the parser)
        NUMBER,     // 42, -3
        STRING,     // 'dairy'
        SYMBOL_OP,  // = != > >= < <=
        AND, OR, NOT, TRUE, FALSE,
        LPAREN, RPAREN, COMMA,
        EOF
    }

    public String describe() {
        return switch (type) {
            case EOF -> "end of rule";
            case STRING -> "'" + text + "'";
            default -> "'" + text + "'";
        };
    }
}
