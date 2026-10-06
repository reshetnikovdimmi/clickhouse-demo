package org.example.clickhouse.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDatabase() {
        log.info("Initializing ClickHouse database for Poker...");

        try {
            // 1. Создаем базу данных если не существует
            jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS app_db");
            log.info("✅ Database app_db created or already exists");

            // 2. Создаем базовые таблицы (user_events - существующие)
            createUserEventsTable();

            // 3. Создаем покерные таблицы из SQL файла
            createPokerTables();

            // 4. Проверяем, есть ли данные
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_db.poker_games", Long.class);
            if (count == 0) {
                log.info("No poker games found, ready for new games");
            } else {
                log.info("Found {} poker games in database", count);
            }

            log.info("✅ Database initialization completed!");

        } catch (Exception e) {
            log.error("Failed to initialize database", e);
        }
    }

    private void createUserEventsTable() {
        try {
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS app_db.user_events (
                    event_id UInt64,
                    user_id String,
                    event_type String,
                    page_url String,
                    user_agent String,
                    ip_address String,
                    event_time DateTime,
                    event_value Float64,
                    created_at DateTime DEFAULT now()
                ) ENGINE = MergeTree()
                ORDER BY (event_time, event_id)
                PARTITION BY toYYYYMM(event_time)
                """;

            jdbcTemplate.execute(createTableSQL);
            log.info("✅ Table user_events created or already exists");

        } catch (Exception e) {
            log.warn("User events table creation: {}", e.getMessage());
        }
    }

    private void createPokerTables() {
        try {
            // Читаем SQL файл с созданием таблиц
            ClassPathResource resource = new ClassPathResource("db/migration/V4__Create_poker_tables.sql");

            try (InputStream inputStream = resource.getInputStream()) {
                byte[] data = FileCopyUtils.copyToByteArray(inputStream);
                String sql = new String(data, StandardCharsets.UTF_8);

                // Разделяем SQL на отдельные запросы (по ;)
                String[] statements = sql.split(";");

                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--") && !trimmed.startsWith("COMMENT")) {
                        try {
                            jdbcTemplate.execute(trimmed);
                            log.info("✅ Executed: {}", trimmed.substring(0, Math.min(50, trimmed.length())));
                        } catch (Exception e) {
                            log.warn("Could not execute: {} - {}", trimmed.substring(0, Math.min(50, trimmed.length())), e.getMessage());
                        }
                    }
                }
            }

            log.info("✅ Poker tables created successfully");

        } catch (Exception e) {
            log.error("Failed to create poker tables", e);
        }
    }
}