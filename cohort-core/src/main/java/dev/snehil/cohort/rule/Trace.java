package dev.snehil.cohort.rule;

import java.util.List;

/**
 * The explanation of one evaluation: a tree mirroring the rule tree, where each node says
 * whether it passed and, for conditions, what the actual value was.
 *
 * <pre>
 * ✗ AND
 *   ✓ orders_30d >= 4          (actual: 6)
 *   ✗ days_since_last_order > 14 (actual: 3)
 * </pre>
 */
public record Trace(String label, boolean passed, String detail, List<Trace> children) {
    public Trace {
        children = List.copyOf(children);
    }

    public static Trace leaf(String label, boolean passed, String detail) {
        return new Trace(label, passed, detail, List.of());
    }

    /** Human-readable, indented tree. */
    public String pretty() {
        StringBuilder sb = new StringBuilder();
        append(sb, 0);
        return sb.toString();
    }

    private void append(StringBuilder sb, int depth) {
        sb.append("  ".repeat(depth)).append(passed ? "✓ " : "✗ ").append(label);
        if (detail != null && !detail.isEmpty()) sb.append("   (").append(detail).append(')');
        sb.append('\n');
        for (Trace child : children) child.append(sb, depth + 1);
    }

    /** The conditions that failed, flattened. Useful for "why not in this cohort?". */
    public List<Trace> failedLeaves() {
        if (children.isEmpty()) return passed ? List.of() : List.of(this);
        return children.stream().flatMap(c -> c.failedLeaves().stream()).toList();
    }
}
