package org.example.clickhouse.controller;

import org.example.clickhouse.grpc.EventGrpcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/grpc")
@RequiredArgsConstructor
public class GrpcController {

    private final EventGrpcService eventGrpcService;

    @GetMapping("/stats")
    public Map<String, Object> getGrpcStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_events_processed", eventGrpcService.getTotalEventsProcessed());
        stats.put("status", "active");
        stats.put("port", 9090);
        return stats;
    }
}
