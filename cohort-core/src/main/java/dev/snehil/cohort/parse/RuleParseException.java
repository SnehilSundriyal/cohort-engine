package dev.snehil.cohort.parse;

/**
 * A rule that can't be parsed or doesn't type-check. Carries the 0-based position so callers can
 * show a caret under the problem:
 *
 * <pre>
 * orders_30d >= 4 AND days_since_last_ordr > 14
 *                     ^ unknown attribute 'days_since_last_ordr' (did you mean 'days_since_last_order'?)
 * </pre>
 */
public class RuleParseException extends RuntimeException {
    private final String input;
    private final int position;
    private final String reason;

    public RuleParseException(String reason, String input, int position) {
        super(reason + " at position " + position);
        this.reason = reason;
        this.input = input;
        this.position = position;
    }

    public int position() {
        return position;
    }

    public String reason() {
        return reason;
    }

    public String input() {
        return input;
    }

    /** The rule text with a caret under the error. */
    public String pointer() {
        return input + "\n" + " ".repeat(Math.max(0, position)) + "^ " + reason;
    }
}
