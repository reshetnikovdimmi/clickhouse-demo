package org.example.clickhouse.grpc;


import org.example.clickhouse.model.UserEvent;
import org.example.clickhouse.repository.UserEventRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class EventGrpcService extends com.example.clickhouse.grpc.EventServiceGrpc.EventServiceImplBase {

    private final UserEventRepository userEventRepository;
    private final AtomicInteger eventCounter = new AtomicInteger(0);

    @Override
    public StreamObserver<com.example.clickhouse.grpc.EventRequest> streamEvents(StreamObserver<com.example.clickhouse.grpc.EventResponse> responseObserver) {
        log.info("New bidirectional stream established");

        return new StreamObserver<com.example.clickhouse.grpc.EventRequest>() {
            private int processedCount = 0;

            @Override
            public void onNext(com.example.clickhouse.grpc.EventRequest request) {
                try {
                    log.info("Received event from user: {}, type: {}",
                            request.getUserId(), request.getEventType());

                    UserEvent event = convertToUserEvent(request);
                    userEventRepository.insert(event);

                    processedCount++;
                    eventCounter.incrementAndGet();

                    com.example.clickhouse.grpc.EventResponse response = com.example.clickhouse.grpc.EventResponse.newBuilder()
                            .setSuccess(true)
                            .setMessage("Event processed successfully")
                            .setEventId(event.getEventId())
                            .setReceivedAt(LocalDateTime.now().toString())
                            .setProcessedCount(processedCount)
                            .build();

                    responseObserver.onNext(response);

                } catch (Exception e) {
                    log.error("Error processing event", e);
                    com.example.clickhouse.grpc.EventResponse errorResponse = com.example.clickhouse.grpc.EventResponse.newBuilder()
                            .setSuccess(false)
                            .setMessage("Error: " + e.getMessage())
                            .build();
                    responseObserver.onNext(errorResponse);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Stream error", t);
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                log.info("Stream completed. Total events processed: {}", processedCount);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void sendEvent(com.example.clickhouse.grpc.EventRequest request, StreamObserver<com.example.clickhouse.grpc.EventResponse> responseObserver) {
        try {
            log.info("Received single event from user: {}, type: {}",
                    request.getUserId(), request.getEventType());

            UserEvent event = convertToUserEvent(request);
            userEventRepository.insert(event);
            eventCounter.incrementAndGet();

            com.example.clickhouse.grpc.EventResponse response = com.example.clickhouse.grpc.EventResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Event saved successfully")
                    .setEventId(event.getEventId())
                    .setReceivedAt(LocalDateTime.now().toString())
                    .setProcessedCount(eventCounter.get())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error processing single event", e);
            com.example.clickhouse.grpc.EventResponse errorResponse = com.example.clickhouse.grpc.EventResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + e.getMessage())
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    private UserEvent convertToUserEvent(com.example.clickhouse.grpc.EventRequest request) {
        LocalDateTime eventTime = request.getTimestamp() > 0 ?
                Instant.ofEpochSecond(request.getTimestamp())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime() :
                LocalDateTime.now();

        return UserEvent.builder()
                .eventId(System.currentTimeMillis())
                .userId(request.getUserId())
                .eventType(request.getEventType())
                .pageUrl(request.getPageUrl())
                .userAgent(request.getUserAgent())
                .ipAddress(request.getIpAddress())
                .eventTime(eventTime)
                .eventValue(request.getEventValue())
                .createdAt(LocalDateTime.now())
                .build();
    }

    public int getTotalEventsProcessed() {
        return eventCounter.get();
    }
}