package dev.snehil.cohort.domain;

import dev.snehil.cohort.rule.Rule;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named segment of users defined by a rule.
 *
 * @param id       URL-safe slug, e.g. {@code at-risk-regular}
 * @param priority higher wins when a user may belong to exactly one cohort
 * @param ruleText the rule as the author wrote it (kept for display and storage)
 * @param rule     the parsed rule tree
 * @param version  bumped on every update, so a stored membership says which definition produced it
 */
public record Cohort(String id, String name, int priority, String ruleText, Rule rule, int version) {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");

    public Cohort {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(ruleText, "ruleText");
        Objects.requireNonNull(rule, "rule");
        if (!SLUG.matcher(id).matches())
            throw new IllegalArgumentException("cohort id must be a lower-case slug: " + id);
        if (name.isBlank()) throw new IllegalArgumentException("cohort needs a name");
        if (version < 1) throw new IllegalArgumentException("version starts at 1");
    }

    public Cohort withVersion(int newVersion) {
        return new Cohort(id, name, priority, ruleText, rule, newVersion);
    }

    /** Turns a display name into an id: "At-risk regulars!" becomes "at-risk-regulars". */
    public static String slugOf(String name) {
        String slug = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) throw new IllegalArgumentException("can't make an id from name: " + name);
        return slug.length() > 63 ? slug.substring(0, 63) : slug;
    }
}
