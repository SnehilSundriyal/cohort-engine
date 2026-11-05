package dev.snehil.cohort.api;

import dev.snehil.cohort.catalog.DefaultCohorts;
import dev.snehil.cohort.domain.AttributeType;
import dev.snehil.cohort.domain.Category;
import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.domain.Customer;
import dev.snehil.cohort.domain.Order;
import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.domain.Value;
import dev.snehil.cohort.engine.AssignmentStrategy;
import dev.snehil.cohort.engine.CohortEngine;
import dev.snehil.cohort.parse.RuleParser;
import dev.snehil.cohort.repo.CohortRepository;
import dev.snehil.cohort.repo.Membership;
import dev.snehil.cohort.repo.MembershipRepository;
import dev.snehil.cohort.rule.EvalContext;
import dev.snehil.cohort.stats.OrderStatsCalculator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application service: the use cases behind the REST endpoints. Talks only to cohort-core
 * interfaces; the controllers only translate HTTP to and from these calls.
 *
 * <p>The engine is an immutable snapshot of the catalog. Every catalog change builds a new one and
 * swaps it in atomically, so in-flight evaluations never see a half-updated catalog.
 */
@Service
public class CohortService {
    private final CohortRepository cohorts;
    private final MembershipRepository memberships;
    private final RuleParser parser;
    private final OrderStatsCalculator calculator;
    private final Clock clock;
    private final AtomicReference<CohortEngine> engine = new AtomicReference<>();

    public CohortService(CohortRepository cohorts, MembershipRepository memberships, RuleParser parser,
                         OrderStatsCalculator calculator, Clock clock,
                         @org.springframework.beans.factory.annotation.Value("${cohort.seed-defaults:true}") boolean seedDefaults) {
        this.cohorts = cohorts;
        this.memberships = memberships;
        this.parser = parser;
        this.calculator = calculator;
        this.clock = clock;
        if (seedDefaults && cohorts.findAll().isEmpty()) {
            DefaultCohorts.load(parser).forEach(cohorts::save);
        }
        refreshEngine();
    }

    // ---- catalog ----

    /** Creates or replaces a cohort. Throws RuleParseException (→ 400) if the rule is invalid. */
    public Cohort save(String name, int priority, String ruleText, String explicitId) {
        String id = explicitId != null && !explicitId.isBlank() ? explicitId : Cohort.slugOf(name);
        Cohort saved = cohorts.save(new Cohort(id, name, priority, ruleText.strip(), parser.parse(ruleText), 1));
        refreshEngine();
        return saved;
    }

    public List<Cohort> list() {
        return engine.get().cohorts();
    }

    public Optional<Cohort> find(String id) {
        return engine.get().cohort(id);
    }

    public boolean delete(String id) {
        boolean removed = cohorts.delete(id);
        if (removed) refreshEngine();
        return removed;
    }

    /** Parses without saving: for a rule editor's live validation. */
    public dev.snehil.cohort.rule.Rule validate(String ruleText) {
        return parser.parse(ruleText);
    }

    private void refreshEngine() {
        engine.set(new CohortEngine(cohorts.findAll()));
    }

    // ---- evaluation ----

    /** {@code premium} is optional (defaults to false); {@code amountPaisa} is required. */
    public record OrderInput(String id, LocalDate date, Long amountPaisa, List<String> categories, Boolean premium) {}

    public record ProfileInput(String city, LocalDate signupDate, List<OrderInput> orders, Map<String, Object> attributes) {}

    /**
     * Builds the user's profile (from orders, from explicit attributes, or both, with explicit
     * attributes winning), assigns cohorts, stores the result and returns it with explanations.
     */
    public CohortEngine.Evaluation evaluate(String userId, ProfileInput input, String mode, LocalDate asOf) {
        LocalDate today = asOf != null ? asOf : LocalDate.now(clock);
        UserProfile profile = buildProfile(userId, input, today);
        CohortEngine.Evaluation result = engine.get().evaluate(profile, AssignmentStrategy.fromName(mode), EvalContext.on(today));

        Instant now = clock.instant();
        memberships.replace(userId, result.matches().stream()
                .map(m -> new Membership(userId, m.cohort().id(), m.cohort().version(), now))
                .toList());
        return result;
    }

    public List<Membership> memberships(String userId) {
        return memberships.findByUser(userId);
    }

    UserProfile buildProfile(String userId, ProfileInput input, LocalDate today) {
        Map<String, Value> attributes = new HashMap<>();
        if (input.orders() != null || input.city() != null) {
            List<Order> orders = new ArrayList<>();
            List<OrderInput> raw = input.orders() == null ? List.of() : input.orders();
            for (int i = 0; i < raw.size(); i++) orders.add(toOrder(userId, i, raw.get(i)));
            Customer customer = new Customer(userId,
                    input.city() == null ? "unknown" : input.city().toLowerCase(Locale.ROOT),
                    input.signupDate() == null ? today : input.signupDate());
            attributes.putAll(calculator.compute(customer, orders, today).attributes());
        }
        if (input.attributes() != null) {
            input.attributes().forEach((name, raw) -> attributes.put(name, toValue(name, raw)));
        }
        return new UserProfile(userId, attributes);
    }

    private static Order toOrder(String userId, int index, OrderInput in) {
        if (in.date() == null) throw new BadInputException("orders[" + index + "].date is required");
        if (in.amountPaisa() == null) throw new BadInputException("orders[" + index + "].amountPaisa is required");
        if (in.categories() == null || in.categories().isEmpty()) {
            throw new BadInputException("orders[" + index + "].categories needs at least one category");
        }
        List<Category> categories = new ArrayList<>();
        for (String c : in.categories()) {
            try {
                categories.add(Category.valueOf(c.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new BadInputException("orders[" + index + "]: unknown category '" + c + "'");
            }
        }
        String id = in.id() != null ? in.id() : userId + "-o" + index;
        return new Order(id, userId, in.date(), in.amountPaisa(), java.util.EnumSet.copyOf(categories),
                Boolean.TRUE.equals(in.premium()));
    }

    /** JSON value → typed attribute value, checked against the schema. */
    private static Value toValue(String name, Object raw) {
        AttributeType type = OrderStatsCalculator.SCHEMA.typeOf(name)
                .orElseThrow(() -> new BadInputException("unknown attribute '" + name + "'"));
        try {
            return switch (type) {
                case NUMBER -> Value.num(((Number) raw).longValue());
                case BOOLEAN -> Value.bool((Boolean) raw);
                case TEXT -> Value.text((String) raw);
                case DATE -> Value.date(LocalDate.parse((String) raw));
                case TEXT_SET -> Value.textSet(((Collection<?>) raw).stream().map(String::valueOf).toList());
            };
        } catch (ClassCastException | NullPointerException | DateTimeParseException e) {
            throw new BadInputException("attribute '" + name + "' must be a " + type);
        }
    }

    public static class BadInputException extends RuntimeException {
        public BadInputException(String message) {
            super(message);
        }
    }
}
