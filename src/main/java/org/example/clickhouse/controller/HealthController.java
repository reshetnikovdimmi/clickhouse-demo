package org.example.clickhouse.controller;

import lombok.RequiredArgsConstructor;
import org.example.clickhouse.repository.PingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "clickhouse-demo");
        status.put("database", "ClickHouse");
        return status;
    }
}
