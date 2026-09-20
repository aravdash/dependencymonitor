package com.dependencyhealth.dashboard;

import com.dependencyhealth.contract.DependencyEvent;
import com.dependencyhealth.contract.Severity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {
    private static final String COLUMNS = "event_id, source, package_name, ecosystem, severity, summary, detail, event_timestamp";
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EventRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public boolean save(DependencyEvent event) {
        String detail;
        try {
            detail = objectMapper.writeValueAsString(event.detail());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Event detail cannot be serialized", exception);
        }
        return jdbc.update("""
                INSERT INTO dependency_event
                    (event_id, source, package_name, ecosystem, severity, summary, detail, event_timestamp)
                VALUES (:id, :source, :name, :ecosystem, :severity, :summary, CAST(:detail AS jsonb), :timestamp)
                ON CONFLICT (event_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.fromString(event.eventId()))
                .addValue("source", event.source())
                .addValue("name", event.packageName())
                .addValue("ecosystem", event.ecosystem())
                .addValue("severity", event.severity().name().toLowerCase(Locale.ROOT))
                .addValue("summary", event.summary())
                .addValue("detail", detail)
                .addValue("timestamp", Timestamp.from(event.timestamp()))) > 0;
    }

    public PageResponse<DependencyEvent> recentEvents(String ecosystem, int limit, int offset) {
        return events(null, ecosystem, limit, offset);
    }

    public PageResponse<DependencyEvent> packageEvents(String name, String ecosystem, int limit, int offset) {
        return events(name, ecosystem, limit, offset);
    }

    private PageResponse<DependencyEvent> events(String name, String ecosystem, int limit, int offset) {
        MapSqlParameterSource parameters = pageParameters(limit, offset);
        List<String> filters = new ArrayList<>();
        if (name != null) {
            filters.add("package_name = :name");
            parameters.addValue("name", name);
        }
        if (ecosystem != null) {
            filters.add("ecosystem = :ecosystem");
            parameters.addValue("ecosystem", ecosystem);
        }
        String where = filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters);
        List<DependencyEvent> items = jdbc.query("SELECT " + COLUMNS + " FROM dependency_event" + where
                + " ORDER BY event_timestamp DESC, event_id DESC LIMIT :limit OFFSET :offset",
                parameters, this::readEvent);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM dependency_event" + where, parameters, Long.class);
        return new PageResponse<>(items, limit, offset, total == null ? 0 : total);
    }

    public PageResponse<PackageStatus> packages(String ecosystem, int limit, int offset) {
        MapSqlParameterSource parameters = pageParameters(limit, offset);
        String where = "";
        if (ecosystem != null) {
            where = " WHERE ecosystem = :ecosystem";
            parameters.addValue("ecosystem", ecosystem);
        }
        // Select the page of distinct packages before joining findings, so page size is
        // independent of how many sources have reported on each package.
        String sql = """
                WITH selected_packages AS (
                    SELECT package_name, ecosystem, MAX(event_timestamp) AS last_updated
                    FROM dependency_event
                """ + where + """
                    GROUP BY package_name, ecosystem
                    ORDER BY last_updated DESC, package_name ASC, ecosystem ASC
                    LIMIT :limit OFFSET :offset
                ), latest AS (
                    SELECT DISTINCT ON (e.package_name, e.ecosystem, e.source)
                        e.*, p.last_updated
                    FROM dependency_event e JOIN selected_packages p
                        ON e.package_name = p.package_name AND e.ecosystem = p.ecosystem
                    ORDER BY e.package_name, e.ecosystem, e.source, e.event_timestamp DESC, e.event_id DESC
                )
                SELECT * FROM latest
                ORDER BY last_updated DESC, package_name ASC, ecosystem ASC, source ASC
                """;
        record PackageKey(String name, String ecosystem) { }
        Map<PackageKey, List<DependencyEvent>> grouped = new LinkedHashMap<>();
        jdbc.query(sql, parameters, (ResultSet resultSet) -> {
            DependencyEvent event = readEvent(resultSet, 0);
            grouped.computeIfAbsent(new PackageKey(event.packageName(), event.ecosystem()),
                    ignored -> new ArrayList<>()).add(event);
        });
        List<PackageStatus> items = grouped.entrySet().stream().map(entry -> new PackageStatus(
                entry.getKey().name(), entry.getKey().ecosystem(),
                entry.getValue().stream().map(DependencyEvent::timestamp).max(java.time.Instant::compareTo).orElseThrow(),
                entry.getValue())).toList();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT package_name, ecosystem FROM dependency_event"
                + where + " GROUP BY package_name, ecosystem) packages", parameters, Long.class);
        return new PageResponse<>(items, limit, offset, total == null ? 0 : total);
    }

    private static MapSqlParameterSource pageParameters(int limit, int offset) {
        return new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset);
    }

    private DependencyEvent readEvent(ResultSet result, int row) throws SQLException {
        Map<String, Object> detail;
        try {
            detail = objectMapper.readValue(result.getString("detail"), new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new SQLException("Stored event detail is invalid JSON", exception);
        }
        return new DependencyEvent(result.getString("event_id"), result.getString("source"),
                result.getString("package_name"), result.getString("ecosystem"),
                Severity.valueOf(result.getString("severity").toUpperCase(Locale.ROOT)),
                result.getString("summary"), detail, result.getTimestamp("event_timestamp").toInstant());
    }
}
