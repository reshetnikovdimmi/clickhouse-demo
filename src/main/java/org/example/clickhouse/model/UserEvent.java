package org.example.clickhouse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {
    private Long eventId;
    private String userId;
    private String eventType;
    private String pageUrl;
    private String userAgent;
    private String ipAddress;
    private LocalDateTime eventTime;
    private Double eventValue;
    private LocalDateTime createdAt;
}