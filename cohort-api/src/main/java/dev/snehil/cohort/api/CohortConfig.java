package dev.snehil.cohort.api;

import dev.snehil.cohort.parse.RuleParser;
import dev.snehil.cohort.repo.CohortRepository;
import dev.snehil.cohort.repo.InMemoryCohortRepository;
import dev.snehil.cohort.repo.InMemoryMembershipRepository;
import dev.snehil.cohort.repo.JdbcCohortRepository;
import dev.snehil.cohort.repo.JdbcMembershipRepository;
import dev.snehil.cohort.repo.JdbcSchema;
import dev.snehil.cohort.repo.MembershipRepository;
import dev.snehil.cohort.rule.op.OperatorRegistry;
import dev.snehil.cohort.stats.OrderStatsCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Wires cohort-core into Spring. The core classes have no Spring annotations; this is the only
 * place that knows about both. Storage is picked by profile: in-memory by default, PostgreSQL
 * with {@code --spring.profiles.active=postgres}.
 */
@Configuration
public class CohortConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    RuleParser ruleParser() {
        return new RuleParser(OperatorRegistry.defaults(), OrderStatsCalculator.SCHEMA);
    }

    @Bean
    OrderStatsCalculator orderStatsCalculator() {
        return new OrderStatsCalculator();
    }

    @Configuration
    @Profile("!postgres")
    static class InMemoryStorage {
        @Bean
        MembershipRepository membershipRepository() {
            return new InMemoryMembershipRepository();
        }

        @Bean
        CohortRepository cohortRepository(MembershipRepository memberships) {
            return new InMemoryCohortRepository(memberships);
        }
    }

    @Configuration
    @Profile("postgres")
    static class PostgresStorage {
        @Bean
        MembershipRepository membershipRepository(DataSource dataSource) {
            JdbcSchema.create(dataSource);
            return new JdbcMembershipRepository(dataSource);
        }

        @Bean
        CohortRepository cohortRepository(DataSource dataSource, RuleParser parser, MembershipRepository memberships) {
            return new JdbcCohortRepository(dataSource, parser::parse);
        }
    }
}
