package org.example.clickhouse.repository;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PingRepository {

    private final JdbcTemplate jdbcTemplate;

    public  String ping() {
        try {
            String result = jdbcTemplate.queryForObject(
                    "SELECT 'ClickHouse connection OK'",
                    String.class
            );
            log.info("✅ Ping successful: {}", result);
            return result;
        } catch (Exception e) {
            log.error("❌ ClickHouse connection failed", e);
            return "Connection failed: " + e.getMessage();
        }
    }
    public String getVersion() {
        return jdbcTemplate.queryForObject("SELECT version()", String.class);
    }
}
