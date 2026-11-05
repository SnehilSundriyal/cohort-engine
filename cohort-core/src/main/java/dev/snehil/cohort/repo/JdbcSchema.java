package dev.snehil.cohort.repo;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the tables from {@code cohort-schema.sql} if they don't exist. Idempotent. */
public final class JdbcSchema {
    private JdbcSchema() {}

    public static void create(DataSource dataSource) {
        String sql;
        try (InputStream in = JdbcSchema.class.getResourceAsStream("/cohort-schema.sql")) {
            if (in == null) throw new IllegalStateException("cohort-schema.sql not on the classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) st.execute(statement);
            }
        } catch (SQLException e) {
            throw new RepositoryException("creating schema", e);
        }
    }
}
