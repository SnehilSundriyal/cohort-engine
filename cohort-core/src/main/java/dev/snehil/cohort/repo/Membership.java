package dev.snehil.cohort.repo;

import java.time.Instant;

/** A stored assignment: user U was in cohort C (definition version V) as of {@code assignedAt}. */
public record Membership(String userId, String cohortId, int cohortVersion, Instant assignedAt) {}
