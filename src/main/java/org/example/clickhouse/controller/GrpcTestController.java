package org.example.clickhouse.controller;

import com.example.clickhouse.grpc.EventRequest;
import com.example.clickhouse.grpc.EventResponse;
import com.example.clickhouse.grpc.EventServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/grpc-test")
@RequiredArgsConstructor
public class GrpcTestController {

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("status", "active");
        stats.put("grpc_port", 9090);
        stats.put("timestamp", LocalDateTime.now().toString());
        stats.put("message", "gRPC server is running on port 9090");
        return stats;
    }

    @PostMapping("/send")
    public Map<String, Object> sendTestEvent() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Создаем gRPC клиент
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                    .usePlaintext()
                    .build();

            EventServiceGrpc.EventServiceBlockingStub stub = EventServiceGrpc.newBlockingStub(channel);

            // Создаем запрос - используем newBuilder() для сгенерированного класса
            EventRequest request = EventRequest.newBuilder()
                    .setUserId("test_user_" + System.currentTimeMillis() % 1000)
                    .setEventType("test_event")
                    .setPageUrl("/test")
                    .setUserAgent("Test Client")
                    .setIpAddress("127.0.0.1")
                    .setEventValue(100.0)
                    .setTimestamp(System.currentTimeMillis() / 1000)
                    .build();

            // Отправляем запрос
            EventResponse response = stub.sendEvent(request);

            result.put("status", "ok");
            result.put("message", "Test event sent via gRPC");
            result.put("response", response);
            result.put("timestamp", LocalDateTime.now().toString());

            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Error sending gRPC event", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }
}