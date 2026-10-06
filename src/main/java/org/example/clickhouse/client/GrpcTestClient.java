package org.example.clickhouse.client;

import com.example.clickhouse.grpc.EventRequest;
import com.example.clickhouse.grpc.EventResponse;
import com.example.clickhouse.grpc.EventServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class GrpcTestClient {

    public static void main(String[] args) {
        // Создаем канал для подключения к gRPC серверу
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        try {
            // Создаем блокирующий stub
            EventServiceGrpc.EventServiceBlockingStub stub = EventServiceGrpc.newBlockingStub(channel);

            // Создаем запрос
            EventRequest request = EventRequest.newBuilder()
                    .setUserId("test_client_user")
                    .setEventType("test_event")
                    .setPageUrl("/test")
                    .setUserAgent("gRPC Client")
                    .setIpAddress("127.0.0.1")
                    .setEventValue(100.5)
                    .setTimestamp(System.currentTimeMillis() / 1000)
                    .build();

            log.info("Sending event: user={}, type={}", request.getUserId(), request.getEventType());

            // Отправляем запрос и получаем ответ
            EventResponse response = stub.sendEvent(request);

            log.info("Response: success={}, message={}, eventId={}, processedCount={}",
                    response.getSuccess(),
                    response.getMessage(),
                    response.getEventId(),
                    response.getProcessedCount());

        } catch (Exception e) {
            log.error("gRPC call failed", e);
        } finally {
            // Закрываем канал
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
