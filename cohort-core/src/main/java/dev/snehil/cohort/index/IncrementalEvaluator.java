package dev.snehil.cohort.index;

import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.domain.UserProfile;
import dev.snehil.cohort.rule.EvalContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

/**
 * Keeps every user's cohort membership up to date as profiles change, re-evaluating only the
 * cohorts that could have changed.
 *
 * <p>On each update it diffs the old and new profile, looks up the cohorts reading the changed
 * attributes in the {@link AttributeIndex}, adds the time-dependent cohorts if the date moved on,
 * and evaluates just those. Everything else keeps its previous answer, which is safe because a
 * rule's result depends only on the attributes it reads (and, for time-dependent operators, the
 * date). Listeners hear about every JOINED / LEFT transition (Observer pattern).
 *
 * <p>Thread-safety: different users may be updated concurrently. Updates for the same user must
 * be serialized by the caller, e.g. by partitioning the change stream by user id.
 */
public final class IncrementalEvaluator {
    private final AttributeIndex index;
    private final Map<String, UserState> states = new ConcurrentHashMap<>();
    private final List<MembershipListener> listeners = new CopyOnWriteArrayList<>();
    private final LongAdder evaluated = new LongAdder();
    private final LongAdder skipped = new LongAdder();

    private record UserState(UserProfile profile, LocalDate evaluatedOn, Set<String> members) {}

    /** What one update did. */
    public record Update(String userId, Set<String> changedAttributes, int cohortsEvaluated,
                        List<MembershipEvent> events) {}

    public IncrementalEvaluator(Collection<Cohort> cohorts) {
        this.index = new AttributeIndex(cohorts);
    }

    public void addListener(MembershipListener listener) {
        listeners.add(listener);
    }

    /** Applies a new version of a user's profile. The first call for a user evaluates every cohort. */
    public Update apply(UserProfile profile, EvalContext ctx) {
        UserState previous = states.get(profile.userId());
        Set<String> changed;
        Collection<Cohort> toEvaluate;
        Set<String> members;

        if (previous == null) {
            changed = profile.attributes().keySet();
            toEvaluate = index.all();
            members = new HashSet<>();
        } else {
            changed = previous.profile().changedAttributes(profile);
            Set<Cohort> affected = new LinkedHashSet<>(index.cohortsReading(changed));
            if (!ctx.today().equals(previous.evaluatedOn())) affected.addAll(index.timeDependent());
            toEvaluate = affected;
            members = new HashSet<>(previous.members());
        }

        List<MembershipEvent> events = new ArrayList<>();
        for (Cohort c : toEvaluate) {
            boolean now = c.rule().matches(profile, ctx);
            boolean before = members.contains(c.id());
            if (now && !before) {
                members.add(c.id());
                events.add(new MembershipEvent(profile.userId(), c.id(), MembershipEvent.Kind.JOINED, ctx.today()));
            } else if (!now && before) {
                members.remove(c.id());
                events.add(new MembershipEvent(profile.userId(), c.id(), MembershipEvent.Kind.LEFT, ctx.today()));
            }
        }

        evaluated.add(toEvaluate.size());
        skipped.add(index.size() - toEvaluate.size());
        states.put(profile.userId(), new UserState(profile, ctx.today(), Set.copyOf(members)));
        for (MembershipEvent e : events) {
            for (MembershipListener l : listeners) l.onChange(e);
        }
        return new Update(profile.userId(), changed, toEvaluate.size(), List.copyOf(events));
    }

    public Optional<Set<String>> membership(String userId) {
        return Optional.ofNullable(states.get(userId)).map(UserState::members);
    }

    public int userCount() {
        return states.size();
    }

    public int cohortCount() {
        return index.size();
    }

    /** Rule evaluations actually performed since the last {@link #resetCounters()}. */
    public long evaluationsPerformed() {
        return evaluated.sum();
    }

    /** Rule evaluations avoided thanks to the index. */
    public long evaluationsSkipped() {
        return skipped.sum();
    }

    public void resetCounters() {
        evaluated.reset();
        skipped.reset();
    }
}
