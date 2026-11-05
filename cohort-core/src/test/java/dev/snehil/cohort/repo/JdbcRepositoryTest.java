package dev.snehil.cohort.repo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static dev.snehil.cohort.Fixtures.PARSER;

/**
 * Runs the same contract against a real PostgreSQL. Enabled when COHORT_TEST_JDBC_URL is set
 * (CI starts a Postgres service container), e.g.
 * {@code COHORT_TEST_JDBC_URL=jdbc:postgresql://localhost:5434/cohort?user=cohort&password=cohort}.
 */
@EnabledIfEnvironmentVariable(named = "COHORT_TEST_JDBC_URL", matches = ".+")
class JdbcRepositoryTest extends RepositoryContract {
    private CohortRepository cohorts;
    private MembershipRepository memberships;

    @BeforeEach
    void setUp() throws SQLException {
        DataSource ds = new DriverManagerDataSource(System.getenv("COHORT_TEST_JDBC_URL"));
        JdbcSchema.create(ds);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("TRUNCATE cohorts, cohort_memberships");
        }
        memberships = new JdbcMembershipRepository(ds);
        cohorts = new JdbcCohortRepository(ds, PARSER::parse);
    }

    @Override protected CohortRepository cohorts() { return cohorts; }
    @Override protected MembershipRepository memberships() { return memberships; }

    /** Minimal DataSource so core needs no connection-pool dependency just for tests. */
    private record DriverManagerDataSource(String url) implements DataSource {
        public Connection getConnection() throws SQLException { return DriverManager.getConnection(url); }
        public Connection getConnection(String u, String p) throws SQLException { return DriverManager.getConnection(url, u, p); }
        public PrintWriter getLogWriter() { return null; }
        public void setLogWriter(PrintWriter out) {}
        public void setLoginTimeout(int seconds) {}
        public int getLoginTimeout() { return 0; }
        public Logger getParentLogger() { return Logger.getGlobal(); }
        public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not a wrapper"); }
        public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
