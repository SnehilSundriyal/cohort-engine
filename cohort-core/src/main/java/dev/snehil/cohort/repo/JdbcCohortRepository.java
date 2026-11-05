package dev.snehil.cohort.repo;

import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.rule.Rule;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * PostgreSQL storage with plain JDBC. Rules are stored as text and re-parsed on load, so the
 * database never holds a serialized Java object and the stored form is human-readable.
 */
public final class JdbcCohortRepository implements CohortRepository {
    private static final String UPSERT = """
            INSERT INTO cohorts (id, name, priority, rule_text, version, updated_at)
            VALUES (?, ?, ?, ?, 1, now())
            ON CONFLICT (id) DO UPDATE
               SET name = excluded.name, priority = excluded.priority, rule_text = excluded.rule_text,
                   version = cohorts.version + 1, updated_at = now()
            RETURNING version
            """;
    private static final String SELECT = "SELECT id, name, priority, rule_text, version FROM cohorts";

    private final DataSource dataSource;
    private final Function<String, Rule> parser;

    public JdbcCohortRepository(DataSource dataSource, Function<String, Rule> parser) {
        this.dataSource = dataSource;
        this.parser = parser;
    }

    @Override
    public Cohort save(Cohort cohort) {
        // One statement: the version bump is atomic in the database, no read-modify-write race.
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(UPSERT)) {
            ps.setString(1, cohort.id());
            ps.setString(2, cohort.name());
            ps.setInt(3, cohort.priority());
            ps.setString(4, cohort.ruleText());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return cohort.withVersion(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RepositoryException("saving cohort " + cohort.id(), e);
        }
    }

    @Override
    public Optional<Cohort> findById(String id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("loading cohort " + id, e);
        }
    }

    @Override
    public List<Cohort> findAll() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + " ORDER BY priority DESC, id");
             ResultSet rs = ps.executeQuery()) {
            List<Cohort> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("loading cohorts", e);
        }
    }

    @Override
    public boolean delete(String id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement m = c.prepareStatement("DELETE FROM cohort_memberships WHERE cohort_id = ?");
                 PreparedStatement d = c.prepareStatement("DELETE FROM cohorts WHERE id = ?")) {
                m.setString(1, id);
                m.executeUpdate();
                d.setString(1, id);
                boolean removed = d.executeUpdate() > 0;
                c.commit();
                return removed;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RepositoryException("deleting cohort " + id, e);
        }
    }

    private Cohort map(ResultSet rs) throws SQLException {
        String ruleText = rs.getString("rule_text");
        return new Cohort(rs.getString("id"), rs.getString("name"), rs.getInt("priority"),
                ruleText, parser.apply(ruleText), rs.getInt("version"));
    }
}
