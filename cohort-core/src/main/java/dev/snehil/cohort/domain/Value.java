package dev.snehil.cohort.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A typed attribute value. Sealed, so every {@code switch} over a Value is checked for
 * exhaustiveness by the compiler: adding a sixth kind breaks every switch that forgot it.
 */
public sealed interface Value permits Value.Num, Value.Bool, Value.Text, Value.Date, Value.TextSet {

    AttributeType type();

    /** How the value is written in rule text. */
    String render();

    record Num(long value) implements Value {
        public AttributeType type() { return AttributeType.NUMBER; }
        public String render() { return Long.toString(value); }
    }

    record Bool(boolean value) implements Value {
        public AttributeType type() { return AttributeType.BOOLEAN; }
        public String render() { return Boolean.toString(value); }
    }

    record Text(String value) implements Value {
        public Text { Objects.requireNonNull(value, "value"); }
        public AttributeType type() { return AttributeType.TEXT; }
        public String render() { return "'" + value.replace("'", "\\'") + "'"; }
    }

    record Date(LocalDate value) implements Value {
        public Date { Objects.requireNonNull(value, "value"); }
        public AttributeType type() { return AttributeType.DATE; }
        public String render() { return "'" + value + "'"; }
    }

    record TextSet(Set<String> values) implements Value {
        public TextSet { values = Set.copyOf(values); }
        public AttributeType type() { return AttributeType.TEXT_SET; }
        public String render() {
            return new TreeSet<>(values).stream().map(v -> "'" + v + "'")
                    .collect(Collectors.joining(", ", "(", ")"));
        }
    }

    static Value num(long v) { return new Num(v); }
    static Value bool(boolean v) { return new Bool(v); }
    static Value text(String v) { return new Text(v); }
    static Value date(LocalDate v) { return new Date(v); }
    static Value textSet(Collection<String> v) { return new TextSet(Set.copyOf(v)); }
}
