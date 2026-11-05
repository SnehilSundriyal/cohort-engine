package dev.snehil.cohort.index;

import java.time.LocalDate;

/** A user entered or left a cohort. */
public record MembershipEvent(String userId, String cohortId, Kind kind, LocalDate on) {
    public enum Kind { JOINED, LEFT }
}
