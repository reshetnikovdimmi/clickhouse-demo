package org.example.clickhouse.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ClickHouseTestContainerTest {

    @Container
    private static final ClickHouseContainer clickHouse = new ClickHouseContainer(
            DockerImageName.parse("clickhouse/clickhouse-server:23.8")
    );

    @Test
    void testClickHouseContainer() throws Exception {
        String jdbcUrl = clickHouse.getJdbcUrl();
        String username = clickHouse.getUsername();
        String password = clickHouse.getPassword();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE DATABASE IF NOT EXISTS test_db");
            stmt.execute("CREATE TABLE test_db.test (id UInt32, name String) ENGINE = Memory");
            stmt.execute("INSERT INTO test_db.test VALUES (1, 'Hello')");

            ResultSet rs = stmt.executeQuery("SELECT * FROM test_db.test");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("id")).isEqualTo(1);
            assertThat(rs.getString("name")).isEqualTo("Hello");
        }
    }
}