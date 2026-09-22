package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL integration tests, executed only by {@code mvn -Ppostgres-it verify}.
 * Docker is required when the profile is selected; unavailable Docker is a test failure.
 * No application context or Kafka broker is needed for this repository-level suite.
 */
@Testcontainers(disabledWithoutDocker = false)
class EventRepositoryIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dependency_health_test")
            .withUsername("test_health")
            .withPassword("test_health");

    private static NamedParameterJdbcTemplate jdbc;
    private static EventRepository repository;
    private static Flyway flyway;

    @BeforeAll
    static void migrateDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        flyway.migrate();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        repository = new EventRepository(jdbc, new ObjectMapper());
    }

    @BeforeEach
    void clearEvents() {
        jdbc.getJdbcTemplate().execute("TRUNCATE TABLE repository_dependency, repository_scan, dependency_event");
    }

    @Test
    void flywayCreatesPostgresJsonbAndTimeZoneAwareSchema() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        Map<String, String> types = jdbc.getJdbcTemplate().query("""
                SELECT column_name, data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'dependency_event'
                """, result -> {
            Map<String, String> values = new LinkedHashMap<>();
            while (result.next()) values.put(result.getString("column_name"), result.getString("data_type"));
            return values;
        });
        assertThat(types).containsEntry("event_id", "uuid")
                .containsEntry("detail", "jsonb")
                .containsEntry("event_timestamp", "timestamp with time zone")
                .containsEntry("received_at", "timestamp with time zone");
    }

    @Test
    void replayedEventIdIsIdempotentAndDoesNotOverwriteTheOriginal() {
        DependencyEvent original = event(1, "lodash", "npm", "osv-cve", Severity.CRITICAL, 10);
        DependencyEvent duplicate = new DependencyEvent(original.eventId(), original.source(), original.packageName(),
                original.ecosystem(), Severity.INFO, "Changed replay payload", Map.of("changed", true),
                original.timestamp().plusSeconds(30));

        assertThat(repository.save(original)).isTrue();
        assertThat(repository.save(original)).isFalse();
        assertThat(repository.save(duplicate)).isFalse();

        PageResponse<DependencyEvent> page = repository.recentEvents(null, 50, 0);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).containsExactly(original);
        assertThat(jdbc.getJdbcTemplate().queryForObject(
                "SELECT received_at IS NOT NULL FROM dependency_event", Boolean.class)).isTrue();
    }

    @Test
    void lateOlderEventsDoNotReplaceLatestFindingsAndSourcesRemainIndependent() {
        DependencyEvent latestGithub = event(1, "lodash", "npm", "github-activity", Severity.INFO, 40);
        DependencyEvent latestOsv = event(2, "lodash", "npm", "osv-cve", Severity.CRITICAL, 30);
        DependencyEvent oldGithub = event(3, "lodash", "npm", "github-activity", Severity.WARNING, 20);
        DependencyEvent oldOsv = event(4, "lodash", "npm", "osv-cve", Severity.INFO, 10);
        // Deliberately arrive in reverse observation order, as can happen during replay.
        List.of(latestGithub, latestOsv, oldGithub, oldOsv).forEach(repository::save);

        PageResponse<PackageStatus> packages = repository.packages(null, 50, 0);
        assertThat(packages.total()).isEqualTo(1);
        assertThat(packages.items()).hasSize(1);
        PackageStatus status = packages.items().get(0);
        assertThat(status.lastUpdated()).isEqualTo(latestGithub.timestamp());
        assertThat(status.latestFindings()).containsExactly(latestGithub, latestOsv);
        assertThat(repository.packageEvents("lodash", "npm", 50, 0).items())
                .containsExactly(latestGithub, latestOsv, oldGithub, oldOsv);
    }

    @Test
    void identicalNamesInDifferentEcosystemsHaveSeparateCurrentStateAndHistory() {
        DependencyEvent npm = event(1, "shared-name", "npm", "license-check", Severity.CRITICAL, 20);
        DependencyEvent pypi = event(2, "shared-name", "pypi", "license-check", Severity.INFO, 10);
        repository.save(npm);
        repository.save(pypi);

        PageResponse<PackageStatus> all = repository.packages(null, 50, 0);
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.items()).extracting(PackageStatus::ecosystem).containsExactly("npm", "pypi");
        assertThat(all.items().get(0).latestFindings()).containsExactly(npm);
        assertThat(all.items().get(1).latestFindings()).containsExactly(pypi);
        assertThat(repository.packages("pypi", 50, 0).items()).containsExactly(all.items().get(1));
        assertThat(repository.packageEvents("shared-name", "npm", 50, 0).items()).containsExactly(npm);
        assertThat(repository.packageEvents("shared-name", "pypi", 50, 0).items()).containsExactly(pypi);
        assertThat(repository.packageEvents("shared-name", null, 50, 0).items()).containsExactly(npm, pypi);
    }

    @Test
    void detailsRoundTripThroughJsonbIncludingNestedValuesAndNull() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("license", "MIT OR Apache-2.0");
        detail.put("metadata", Map.of("reviewed", true, "versions", List.of("1.0", "1.1")));
        detail.put("count", 3);
        detail.put("unknown", null);
        detail.put("summary", "Unicode: café; quote: \"; backslash: \\");
        DependencyEvent event = new DependencyEvent(UUID.randomUUID().toString(), "license-check",
                "@example/core", "npm", Severity.INFO, "License inspected", detail,
                Instant.parse("2025-03-01T00:00:00.123456Z"));
        repository.save(event);

        assertThat(repository.packageEvents("@example/core", "npm", 50, 0).items()).containsExactly(event);
    }

    @Test
    void paginationCountsPackagesRatherThanFindingsAndFiltersBeforePaging() {
        DependencyEvent zeta = event(1, "zeta", "npm", "osv-cve", Severity.INFO, 10);
        DependencyEvent alpha = event(2, "alpha", "npm", "osv-cve", Severity.INFO, 20);
        DependencyEvent sharedPypi = event(3, "shared", "pypi", "osv-cve", Severity.WARNING, 30);
        DependencyEvent sharedNpm = event(4, "shared", "npm", "osv-cve", Severity.CRITICAL, 40);
        DependencyEvent sharedLicense = event(5, "shared", "npm", "license-check", Severity.WARNING, 50);
        List.of(zeta, alpha, sharedPypi, sharedNpm, sharedLicense).forEach(repository::save);

        PageResponse<PackageStatus> first = repository.packages(null, 2, 0);
        assertThat(first.total()).isEqualTo(4);
        assertThat(first.limit()).isEqualTo(2);
        assertThat(first.offset()).isZero();
        assertThat(first.items()).extracting(PackageStatus::packageName).containsExactly("shared", "shared");
        assertThat(first.items().get(0).latestFindings()).containsExactly(sharedLicense, sharedNpm);
        PageResponse<PackageStatus> second = repository.packages(null, 2, 2);
        assertThat(second.total()).isEqualTo(4);
        assertThat(second.items()).extracting(PackageStatus::packageName).containsExactly("alpha", "zeta");
        PageResponse<PackageStatus> filtered = repository.packages("npm", 1, 1);
        assertThat(filtered.total()).isEqualTo(3);
        assertThat(filtered.items()).extracting(PackageStatus::packageName).containsExactly("alpha");

        PageResponse<DependencyEvent> recent = repository.recentEvents(null, 2, 1);
        assertThat(recent.total()).isEqualTo(5);
        assertThat(recent.items()).containsExactly(sharedNpm, sharedPypi);
        assertThat(repository.recentEvents("npm", 2, 1).items()).containsExactly(sharedNpm, alpha);
        PageResponse<DependencyEvent> history = repository.packageEvents("shared", "npm", 1, 1);
        assertThat(history.total()).isEqualTo(2);
        assertThat(history.items()).containsExactly(sharedNpm);
        assertThat(repository.packages(null, 2, 10).items()).isEmpty();
    }

    private static DependencyEvent event(int id, String name, String ecosystem, String source,
                                         Severity severity, int seconds) {
        return new DependencyEvent(UUID.nameUUIDFromBytes(("event-" + id).getBytes(StandardCharsets.UTF_8)).toString(),
                source, name, ecosystem, severity, "Observation " + id, Map.of("observation", id),
                Instant.parse("2025-03-01T00:00:00Z").plusSeconds(seconds));
    }
}
