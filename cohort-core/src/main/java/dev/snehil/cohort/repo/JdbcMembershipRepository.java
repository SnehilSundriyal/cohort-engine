package dev.snehil.cohort.repo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class JdbcMembershipRepository implements MembershipRepository {
    private final DataSource dataSource;

    public JdbcMembershipRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void replace(String userId, List<Membership> memberships) {
        // Delete + batch insert in one transaction: readers see the old set or the new set, never half.
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement("DELETE FROM cohort_memberships WHERE user_id = ?");
                 PreparedStatement ins = c.prepareStatement(
                         "INSERT INTO cohort_memberships (user_id, cohort_id, cohort_version, assigned_at) VALUES (?, ?, ?, ?)")) {
                del.setString(1, userId);
                del.executeUpdate();
                for (Membership m : memberships) {
                    if (!m.userId().equals(userId)) throw new IllegalArgumentException("membership for another user: " + m);
                    ins.setString(1, userId);
                    ins.setString(2, m.cohortId());
                    ins.setInt(3, m.cohortVersion());
                    ins.setTimestamp(4, Timestamp.from(m.assignedAt()));
                    ins.addBatch();
                }
                ins.executeBatch();
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RepositoryException("saving memberships for " + userId, e);
        }
    }

    @Override
    public List<Membership> findByUser(String userId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id, cohort_id, cohort_version, assigned_at FROM cohort_memberships WHERE user_id = ? ORDER BY cohort_id")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Membership> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new Membership(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getTimestamp(4).toInstant()));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RepositoryException("loading memberships for " + userId, e);
        }
    }

    @Override
    public long countByCohort(String cohortId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM cohort_memberships WHERE cohort_id = ?")) {
            ps.setString(1, cohortId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("counting cohort " + cohortId, e);
        }
    }
}
