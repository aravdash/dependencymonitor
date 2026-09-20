package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.repository.DiscoveredDependency;
import com.dependencyhealth.contract.repository.RepositoryInventoryEvent;
import com.dependencyhealth.contract.repository.RepositoryScanRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RepositoryScanRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public RepositoryScanRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RepositoryScanView create(RepositoryScanRequest request) {
        jdbc.update("""
                INSERT INTO repository_scan
                    (request_id, repository_id, repository_url, status, requested_at)
                VALUES (:id, :repositoryId, :url, 'queued', :requestedAt)
                """, new MapSqlParameterSource()
                .addValue("id", UUID.fromString(request.requestId()))
                .addValue("repositoryId", request.repositoryId())
                .addValue("url", request.repositoryUrl())
                .addValue("requestedAt", Timestamp.from(request.requestedAt())));
        return find(request.requestId()).orElseThrow();
    }

    public void markPublicationFailed(String requestId) {
        jdbc.update("""
                UPDATE repository_scan SET status='failed', message='Could not queue the repository scan',
                    completed_at=CURRENT_TIMESTAMP WHERE request_id=:id
                """, new MapSqlParameterSource("id", UUID.fromString(requestId)));
    }

    @Transactional
    public void apply(RepositoryInventoryEvent event) {
        UUID id = UUID.fromString(event.requestId());
        int updated = jdbc.update("""
                UPDATE repository_scan SET status=:status, dependency_count=:count,
                    unsupported_count=:unsupported, message=:message, completed_at=:completed
                WHERE request_id=:id AND repository_id=:repositoryId
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("repositoryId", event.repositoryId())
                .addValue("status", event.status().value()).addValue("count", event.dependencies().size())
                .addValue("unsupported", event.unsupportedCount()).addValue("message", event.message())
                .addValue("completed", Timestamp.from(event.timestamp())));
        if (updated != 1) throw new IllegalStateException("Repository scan request does not exist or does not match");
        jdbc.update("DELETE FROM repository_dependency WHERE request_id=:id", new MapSqlParameterSource("id", id));
        if (!event.dependencies().isEmpty()) {
            var batches = event.dependencies().stream().map(dependency -> dependencyParameters(id, dependency))
                    .toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    INSERT INTO repository_dependency
                        (request_id, package_name, ecosystem, version, package_url, direct, declared_license)
                    VALUES (:id, :name, :ecosystem, :version, :purl, :direct, :license)
                    ON CONFLICT (request_id, ecosystem, package_name, version) DO UPDATE
                        SET direct = repository_dependency.direct OR EXCLUDED.direct,
                            package_url = EXCLUDED.package_url, declared_license = EXCLUDED.declared_license
                    """, batches);
        }
    }

    public Optional<RepositoryScanView> find(String requestId) {
        List<RepositoryScanView> rows = jdbc.query("""
                SELECT request_id, repository_id, repository_url, status, dependency_count,
                    unsupported_count, message, requested_at, completed_at
                FROM repository_scan WHERE request_id=:id
                """, new MapSqlParameterSource("id", UUID.fromString(requestId)), this::readScan);
        return rows.stream().findFirst();
    }

    public PageResponse<RepositoryScanView> recent(int limit, int offset) {
        var params = new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset);
        List<RepositoryScanView> items = jdbc.query("""
                SELECT request_id, repository_id, repository_url, status, dependency_count,
                    unsupported_count, message, requested_at, completed_at
                FROM repository_scan ORDER BY requested_at DESC, request_id DESC LIMIT :limit OFFSET :offset
                """, params, this::readScan);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM repository_scan", params, Long.class);
        return new PageResponse<>(items, limit, offset, total == null ? 0 : total);
    }

    public PageResponse<RepositoryDependencyView> dependencies(String requestId, String ecosystem, int limit, int offset) {
        UUID id = UUID.fromString(requestId);
        var params = new MapSqlParameterSource().addValue("id", id).addValue("limit", limit).addValue("offset", offset);
        String filter = ecosystem == null ? "" : " AND ecosystem=:ecosystem";
        if (ecosystem != null) params.addValue("ecosystem", ecosystem);
        List<RepositoryDependencyView> items = jdbc.query("""
                SELECT package_name, ecosystem, version, package_url, direct, declared_license
                FROM repository_dependency WHERE request_id=:id
                """ + filter + " ORDER BY direct DESC, ecosystem, package_name, version LIMIT :limit OFFSET :offset",
                params, (rs, row) -> new RepositoryDependencyView(rs.getString("package_name"),
                        rs.getString("ecosystem"), rs.getString("version"), rs.getString("package_url"),
                        rs.getBoolean("direct"), rs.getString("declared_license")));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM repository_dependency WHERE request_id=:id" + filter,
                params, Long.class);
        return new PageResponse<>(items, limit, offset, total == null ? 0 : total);
    }

    private MapSqlParameterSource dependencyParameters(UUID id, DiscoveredDependency dependency) {
        return new MapSqlParameterSource().addValue("id", id).addValue("name", dependency.packageName())
                .addValue("ecosystem", dependency.ecosystem()).addValue("version", dependency.version())
                .addValue("purl", dependency.packageUrl()).addValue("direct", dependency.direct())
                .addValue("license", dependency.declaredLicense());
    }
    private RepositoryScanView readScan(ResultSet rs, int row) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        return new RepositoryScanView(rs.getString("request_id"), rs.getString("repository_id"),
                rs.getString("repository_url"), rs.getString("status"), rs.getInt("dependency_count"),
                rs.getInt("unsupported_count"), rs.getString("message"), rs.getTimestamp("requested_at").toInstant(),
                completed == null ? null : completed.toInstant());
    }
}
