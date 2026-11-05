package dev.snehil.cohort.api;

import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.engine.CohortEngine;
import dev.snehil.cohort.repo.Membership;
import dev.snehil.cohort.rule.Trace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response bodies. Kept apart from domain types so the API can evolve separately. */
public final class Dtos {
    private Dtos() {}

    public record CohortRequest(String id, @NotBlank String name, @NotNull Integer priority, @NotBlank String rule) {}

    public record CohortResponse(String id, String name, int priority, String rule, String canonicalRule,
                                 int version, List<String> attributes) {
        static CohortResponse of(Cohort c) {
            return new CohortResponse(c.id(), c.name(), c.priority(), c.ruleText(), c.rule().render(), c.version(),
                    c.rule().attributes().stream().sorted().toList());
        }
    }

    public record RuleRequest(@NotBlank String rule) {}

    public record RuleResponse(boolean valid, String canonicalRule, List<String> attributes, boolean timeDependent) {}

    public record TraceDto(String label, boolean passed, String detail, List<TraceDto> children) {
        static TraceDto of(Trace t) {
            return new TraceDto(t.label(), t.passed(), t.detail(), t.children().stream().map(TraceDto::of).toList());
        }
    }

    public record MatchDto(String id, String name, int priority, int version, TraceDto explanation) {
        static MatchDto of(CohortEngine.Match m) {
            Cohort c = m.cohort();
            return new MatchDto(c.id(), c.name(), c.priority(), c.version(), TraceDto.of(m.trace()));
        }
    }

    public record EvaluationResponse(String userId, String mode, LocalDate asOf, List<MatchDto> cohorts) {
        static EvaluationResponse of(CohortEngine.Evaluation e) {
            return new EvaluationResponse(e.userId(), e.mode(), e.ctx().today(),
                    e.matches().stream().map(MatchDto::of).toList());
        }
    }

    public record MembershipDto(String cohortId, int cohortVersion, Instant assignedAt) {
        static MembershipDto of(Membership m) {
            return new MembershipDto(m.cohortId(), m.cohortVersion(), m.assignedAt());
        }
    }

    public record UserCohortsResponse(String userId, List<MembershipDto> cohorts) {}
}
