package dev.snehil.cohort.bench;

import dev.snehil.cohort.bulk.BulkScorer;
import dev.snehil.cohort.catalog.DefaultCohorts;
import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.domain.Order;
import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.engine.AssignmentStrategy;
import dev.snehil.cohort.engine.CohortEngine;
import dev.snehil.cohort.gen.SyntheticDataGenerator;
import dev.snehil.cohort.gen.SyntheticDataGenerator.GeneratedUser;
import dev.snehil.cohort.gen.SyntheticDataGenerator.Persona;
import dev.snehil.cohort.index.IncrementalEvaluator;
import dev.snehil.cohort.parse.RuleParser;
import dev.snehil.cohort.rule.EvalContext;
import dev.snehil.cohort.rule.op.OperatorRegistry;
import dev.snehil.cohort.stats.OrderStatsCalculator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Supplier;

/**
 * Reproducible benchmarks over synthetic grocery customers.
 *
 * <pre>
 *   ./mvnw -q -pl cohort-core compile exec:java              # 100,000 users
 *   ./mvnw -q -pl cohort-core compile exec:java -Dexec.args=20000
 * </pre>
 *
 * <ol>
 *   <li>Bulk scoring: raw orders → derived attributes → every cohort, on 1..N threads.</li>
 *   <li>Incremental re-evaluation: how many rule evaluations the attribute index avoids,
 *       for an overnight refresh and for a stream of intraday orders, checked against a full
 *       re-score for correctness.</li>
 * </ol>
 */
public final class Benchmark {
    private static final LocalDate DAY0 = LocalDate.of(2026, 11, 1);
    private static final long SEED = 42;
    private static final int WARMUP = 3, RUNS = 7;

    public static void main(String[] args) {
        int users = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int cores = Runtime.getRuntime().availableProcessors();

        RuleParser parser = new RuleParser(OperatorRegistry.defaults(), OrderStatsCalculator.SCHEMA);
        List<Cohort> cohorts = DefaultCohorts.load(parser);
        CohortEngine engine = new CohortEngine(cohorts);
        OrderStatsCalculator calc = new OrderStatsCalculator();
        SyntheticDataGenerator gen = new SyntheticDataGenerator(SEED, DAY0);

        System.out.printf("Java %s, %d cores, max heap %d MB%n", Runtime.version(), cores,
                Runtime.getRuntime().maxMemory() >> 20);
        List<GeneratedUser> population = new ArrayList<>(users);
        for (int i = 0; i < users; i++) population.add(gen.user(i));
        long orders = population.stream().mapToLong(u -> u.orders().size()).sum();
        System.out.printf("%,d users, %,d orders, %d cohorts, as of %s%n%n", users, orders, cohorts.size(), DAY0);

        EvalContext ctx0 = EvalContext.on(DAY0);
        bulk(engine, calc, population, ctx0, cores);
        incremental(cohorts, engine, calc, gen, population);
    }

    // ------------------------------------------------------------------ bulk scoring

    private static void bulk(CohortEngine engine, OrderStatsCalculator calc, List<GeneratedUser> population,
                             EvalContext ctx, int cores) {
        System.out.println("== Bulk scoring: orders -> attributes -> all cohorts (median of " + RUNS + " runs after " + WARMUP + " warm-ups) ==");
        BulkScorer<GeneratedUser> scorer = new BulkScorer<>(engine, new AssignmentStrategy.AllMatching(),
                u -> calc.compute(u.customer(), u.orders(), ctx.today()));

        BulkScorer.Result baseline = null;
        double baseMs = 0;
        Set<Integer> threadCounts = new java.util.TreeSet<>(List.of(1, 2, 4, 8, cores));
        for (int threads : threadCounts) {
            final int t = threads;
            double ms = median(() -> scorer.score(population, ctx, t));
            BulkScorer.Result r = scorer.score(population, ctx, t);
            if (baseline == null) {
                baseline = r;
                baseMs = ms;
            } else if (!r.assignments().equals(baseline.assignments())) {
                throw new AssertionError(threads + " threads gave different assignments than 1 thread");
            }
            System.out.printf("  %2d thread%s  %8.1f ms   %,10.0f users/s   speed-up %.2fx%n",
                    threads, threads == 1 ? " " : "s", ms, population.size() / (ms / 1000), baseMs / ms);
        }
        System.out.println("  (every multi-threaded run produced identical assignments to the single-threaded run)");

        System.out.println("\n  Cohort sizes:");
        for (Map.Entry<String, Long> e : baseline.countsByCohort().entrySet()) {
            System.out.printf("    %-28s %,8d  (%4.1f%%)%n", e.getKey(), e.getValue(), 100.0 * e.getValue() / population.size());
        }
        long unassigned = baseline.assignments().stream().filter(List::isEmpty).count();
        System.out.printf("    %-28s %,8d  (%4.1f%%)%n%n", "(in no cohort)", unassigned, 100.0 * unassigned / population.size());
    }

    private static double median(Supplier<BulkScorer.Result> run) {
        for (int i = 0; i < WARMUP; i++) run.get();
        double[] ms = new double[RUNS];
        for (int i = 0; i < RUNS; i++) ms[i] = run.get().millis();
        Arrays.sort(ms);
        return ms[RUNS / 2];
    }

    // ------------------------------------------------------------------ incremental

    private static void incremental(List<Cohort> cohorts, CohortEngine engine, OrderStatsCalculator calc,
                                    SyntheticDataGenerator gen, List<GeneratedUser> population) {
        System.out.println("== Incremental re-evaluation via the attribute -> cohort index ==");
        int n = population.size();
        IncrementalEvaluator inc = new IncrementalEvaluator(cohorts);
        long[] events = new long[1];
        inc.addListener(e -> events[0]++);

        EvalContext ctx0 = EvalContext.on(DAY0);
        List<List<Order>> orders = new ArrayList<>(n);
        List<UserProfile> profiles = new ArrayList<>(n);
        for (GeneratedUser u : population) {
            orders.add(new ArrayList<>(u.orders()));
            UserProfile p = calc.compute(u.customer(), u.orders(), DAY0);
            profiles.add(p);
            inc.apply(p, ctx0);
        }

        // Scenario A: intraday. 20% of users place an order on DAY0 itself; the date doesn't change.
        SplittableRandom rnd = new SplittableRandom(SEED);
        inc.resetCounters();
        events[0] = 0;
        int updates = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if (rnd.nextInt(5) != 0) continue;
            GeneratedUser u = population.get(i);
            orders.get(i).add(gen.extraOrder(u, DAY0, 1));
            UserProfile p = calc.compute(u.customer(), orders.get(i), DAY0);
            profiles.set(i, p);
            inc.apply(p, ctx0);
            updates++;
        }
        long incNanos = System.nanoTime() - t0;
        report("A. Intraday: " + String.format("%,d", updates) + " new orders, same day", inc, updates, events[0], incNanos);
        verify(engine, inc, profiles, ctx0);

        // Scenario B: overnight refresh. The date moves to DAY0+1; every profile is recomputed
        // (recency ages by a day, 30/90-day windows slide), and some users order on the new day.
        LocalDate day1 = DAY0.plusDays(1);
        EvalContext ctx1 = EvalContext.on(day1);
        inc.resetCounters();
        events[0] = 0;
        int ordered = 0;
        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            GeneratedUser u = population.get(i);
            if (rnd.nextInt(1000) < dailyOrderChancePerMille(u.persona())) {
                orders.get(i).add(gen.extraOrder(u, day1, 2));
                ordered++;
            }
            UserProfile p = calc.compute(u.customer(), orders.get(i), day1);
            profiles.set(i, p);
            inc.apply(p, ctx1);
        }
        incNanos = System.nanoTime() - t0;
        report("B. Overnight refresh: all " + String.format("%,d", n) + " users recomputed for " + day1
                + String.format(" (%,d ordered)", ordered), inc, n, events[0], incNanos);
        verify(engine, inc, profiles, ctx1);
    }

    private static int dailyOrderChancePerMille(Persona p) {
        return switch (p) {
            case DAILY_DAIRY -> 500;
            case NEW_CUSTOMER -> 250;
            case PREMIUM_FOODIE -> 200;
            case WEEKLY_REGULAR -> 140;
            case MONTHLY_STOCKUP -> 33;
            case LAPSING -> 25;
            case ONE_AND_DONE -> 10;
            case CHURNED -> 5;
        };
    }

    private static void report(String title, IncrementalEvaluator inc, int updates, long events, long nanos) {
        long done = inc.evaluationsPerformed();
        long naive = (long) updates * inc.cohortCount();
        System.out.println("  " + title);
        System.out.printf("    rule evaluations: %,d incremental vs %,d full re-score  ->  %.1f%% fewer%n",
                done, naive, 100.0 * (naive - done) / naive);
        System.out.printf("    avg cohorts re-checked per update: %.2f of %d;  membership events: %,d;  %.0f ms%n",
                (double) done / updates, inc.cohortCount(), events, nanos / 1e6);
    }

    /** The incremental membership must equal a from-scratch evaluation for every user. */
    private static void verify(CohortEngine engine, IncrementalEvaluator inc, List<UserProfile> profiles, EvalContext ctx) {
        AssignmentStrategy all = new AssignmentStrategy.AllMatching();
        for (UserProfile p : profiles) {
            Set<String> full = new HashSet<>(all.assign(p, engine.cohorts(), ctx).stream().map(Cohort::id).toList());
            Set<String> incremental = inc.membership(p.userId()).orElseThrow();
            if (!full.equals(incremental)) {
                throw new AssertionError("incremental drifted for " + p.userId() + ": " + incremental + " vs " + full);
            }
        }
        System.out.printf("    verified: identical to a full re-score for all %,d users%n%n", profiles.size());
    }
}
