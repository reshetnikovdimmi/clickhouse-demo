package org.example.clickhouse.service;

import org.example.clickhouse.model.UserEvent;
import org.example.clickhouse.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventService {

    private final UserEventRepository repository;

    public List<UserEvent> getAllEvents() {
        return repository.findAll();
    }

    public List<UserEvent> getEventsByUser(String userId) {
        return repository.findByUserId(userId);
    }

    public List<UserEvent> getEventsByType(String eventType) {
        return repository.findByEventType(eventType);
    }

    public Long getTotalCount() {
        return repository.count();
    }

    public Double getAverageValue() {
        return repository.avgValue();
    }
}
