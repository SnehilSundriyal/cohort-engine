package dev.snehil.cohort.rule.op;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Looks operators up by symbol. The parser asks the registry whether a word like
 * {@code WITHIN_DAYS} is an operator, so a new keyword operator needs no parser or lexer change:
 * open for extension, closed for modification.
 *
 * <p>Immutable: {@link #with} returns a new registry.
 */
public final class OperatorRegistry {
    private final Map<String, Operator> bySymbol;

    private OperatorRegistry(Map<String, Operator> bySymbol) {
        this.bySymbol = Map.copyOf(bySymbol);
    }

    public static OperatorRegistry defaults() {
        Map<String, Operator> m = new LinkedHashMap<>();
        for (Operator op : new Operator[]{
                EqualsOperator.equalTo(), EqualsOperator.notEqualTo(),
                new ComparisonOperator.GreaterThan(), new ComparisonOperator.GreaterOrEqual(),
                new ComparisonOperator.LessThan(), new ComparisonOperator.LessOrEqual(),
                new InOperator(), new BetweenOperator(), new ContainsOperator(), new WithinDaysOperator()}) {
            m.put(op.symbol(), op);
        }
        return new OperatorRegistry(m);
    }

    public OperatorRegistry with(Operator op) {
        Map<String, Operator> m = new LinkedHashMap<>(bySymbol);
        if (m.putIfAbsent(op.symbol().toUpperCase(Locale.ROOT), op) != null) {
            throw new IllegalArgumentException("operator already registered: " + op.symbol());
        }
        return new OperatorRegistry(m);
    }

    public Optional<Operator> find(String symbol) {
        return Optional.ofNullable(bySymbol.get(symbol.toUpperCase(Locale.ROOT)));
    }

    public Collection<Operator> all() {
        return bySymbol.values();
    }
}
