package dev.snehil.cohort.bulk;

import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.engine.AssignmentStrategy;
import dev.snehil.cohort.engine.CohortEngine;
import dev.snehil.cohort.rule.EvalContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Scores many users at once, on one thread or on a fixed pool of N.
 *
 * <p>Design: the input is cut into contiguous chunks; each task owns one chunk, keeps its own
 * per-cohort counters and writes results into its own slice of the output array. Tasks share
 * nothing mutable, so there are no locks or atomics on the hot path; the counters are merged
 * after {@link Future#get()}, which also guarantees the workers' writes are visible.
 *
 * <p>The engine, rules and operators are immutable, so one engine is shared by all threads.
 *
 * @param <T> whatever a user is built from: a ready profile, or raw orders to compute it from
 */
public final class BulkScorer<T> {
    private static final int CHUNK = 2_048;

    private final CohortEngine engine;
    private final AssignmentStrategy strategy;
    private final Function<? super T, UserProfile> profileOf;

    public BulkScorer(CohortEngine engine, AssignmentStrategy strategy, Function<? super T, UserProfile> profileOf) {
        this.engine = engine;
        this.strategy = strategy;
        this.profileOf = profileOf;
    }

    public record Result(int users, Map<String, Long> countsByCohort, List<List<String>> assignments, long nanos) {
        public double millis() {
            return nanos / 1_000_000.0;
        }
    }

    public Result score(List<? extends T> inputs, EvalContext ctx, int threads) {
        if (threads < 1) throw new IllegalArgumentException("threads must be >= 1");
        List<Cohort> cohorts = engine.cohorts();
        @SuppressWarnings("unchecked")
        List<String>[] assignments = new List[inputs.size()];
        long start = System.nanoTime();

        long[] totals;
        if (threads == 1) {
            // Same chunking as the pooled path, on the caller's thread: a fair baseline.
            totals = new long[cohorts.size()];
            for (int from = 0; from < inputs.size(); from += CHUNK) {
                long[] partial = scoreRange(inputs, from, Math.min(inputs.size(), from + CHUNK), ctx, cohorts, assignments);
                for (int i = 0; i < totals.length; i++) totals[i] += partial[i];
            }
        } else {
            totals = new long[cohorts.size()];
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                List<Future<long[]>> futures = new ArrayList<>();
                for (int from = 0; from < inputs.size(); from += CHUNK) {
                    int lo = from, hi = Math.min(inputs.size(), from + CHUNK);
                    futures.add(pool.submit(() -> scoreRange(inputs, lo, hi, ctx, cohorts, assignments)));
                }
                for (Future<long[]> f : futures) {
                    long[] partial = f.get();
                    for (int i = 0; i < totals.length; i++) totals[i] += partial[i];
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("bulk scoring interrupted", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("bulk scoring failed", e.getCause());
            }
        }

        long nanos = System.nanoTime() - start;
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < cohorts.size(); i++) counts.put(cohorts.get(i).id(), totals[i]);
        return new Result(inputs.size(), counts, Arrays.asList(assignments), nanos);
    }

    private long[] scoreRange(List<? extends T> inputs, int from, int to, EvalContext ctx,
                              List<Cohort> cohorts, List<String>[] out) {
        long[] counts = new long[cohorts.size()];
        Map<String, Integer> position = new java.util.HashMap<>();
        for (int i = 0; i < cohorts.size(); i++) position.put(cohorts.get(i).id(), i);

        for (int i = from; i < to; i++) {
            UserProfile profile = profileOf.apply(inputs.get(i));
            List<Cohort> assigned = strategy.assign(profile, cohorts, ctx);
            List<String> ids = new ArrayList<>(assigned.size());
            for (Cohort c : assigned) {
                counts[position.get(c.id())]++;
                ids.add(c.id());
            }
            out[i] = ids;
        }
        return counts;
    }
}
