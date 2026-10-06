package org.example.clickhouse.repository;

import org.example.clickhouse.model.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserEventRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserEvent> rowMapper = (rs, rowNum) -> UserEvent.builder()
            .eventId(rs.getLong("event_id"))
            .userId(rs.getString("user_id"))
            .eventType(rs.getString("event_type"))
            .pageUrl(rs.getString("page_url"))
            .userAgent(rs.getString("user_agent"))
            .ipAddress(rs.getString("ip_address"))
            .eventTime(rs.getObject("event_time", LocalDateTime.class))
            .eventValue(rs.getDouble("event_value"))
            .createdAt(rs.getObject("created_at", LocalDateTime.class))
            .build();

    public List<UserEvent> findAll() {
        String sql = "SELECT * FROM app_db.user_events ORDER BY event_time DESC LIMIT 100";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<UserEvent> findByUserId(String userId) {
        String sql = "SELECT * FROM app_db.user_events WHERE user_id = ? ORDER BY event_time DESC LIMIT 50";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    public List<UserEvent> findByEventType(String eventType) {
        String sql = "SELECT * FROM app_db.user_events WHERE event_type = ? ORDER BY event_time DESC LIMIT 50";
        return jdbcTemplate.query(sql, rowMapper, eventType);
    }

    public Long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_db.user_events", Long.class);
    }

    public Double avgValue() {
        return jdbcTemplate.queryForObject("SELECT AVG(event_value) FROM app_db.user_events", Double.class);
    }

    public int insert(UserEvent event) {
        String sql = "INSERT INTO app_db.user_events " +
                "(event_id, user_id, event_type, page_url, user_agent, ip_address, event_time, event_value, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        return jdbcTemplate.update(sql,
                event.getEventId(),
                event.getUserId(),
                event.getEventType(),
                event.getPageUrl(),
                event.getUserAgent(),
                event.getIpAddress(),
                event.getEventTime(),
                event.getEventValue(),
                event.getCreatedAt()
        );
    }
}
