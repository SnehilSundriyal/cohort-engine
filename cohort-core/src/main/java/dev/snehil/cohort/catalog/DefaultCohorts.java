package dev.snehil.cohort.catalog;

import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.parse.RuleParseException;
import dev.snehil.cohort.parse.RuleParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads {@code default-cohorts.txt}: one {@code priority | name | rule} per line. */
public final class DefaultCohorts {
    private DefaultCohorts() {}

    public static List<Cohort> load(RuleParser parser) {
        try (InputStream in = DefaultCohorts.class.getResourceAsStream("/default-cohorts.txt")) {
            if (in == null) throw new IllegalStateException("default-cohorts.txt not on the classpath");
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), parser);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Cohort> parse(BufferedReader reader, RuleParser parser) throws IOException {
        List<Cohort> out = new ArrayList<>();
        String line;
        int lineNo = 0;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            if (line.isBlank() || line.strip().startsWith("#")) continue;
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3) throw new IllegalArgumentException("line " + lineNo + ": expected 'priority | name | rule'");
            String name = parts[1].strip();
            String ruleText = parts[2].strip();
            try {
                out.add(new Cohort(Cohort.slugOf(name), name, Integer.parseInt(parts[0].strip()),
                        ruleText, parser.parse(ruleText), 1));
            } catch (RuleParseException e) {
                throw new IllegalArgumentException("line " + lineNo + " (" + name + "):\n" + e.pointer(), e);
            }
        }
        return out;
    }
}
