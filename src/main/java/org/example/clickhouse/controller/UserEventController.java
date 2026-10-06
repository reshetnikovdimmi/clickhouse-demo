package org.example.clickhouse.controller;

import org.example.clickhouse.model.UserEvent;
import org.example.clickhouse.service.UserEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class UserEventController {

    private final UserEventService userEventService;

    @GetMapping
    public List<UserEvent> getAllEvents() {
        return userEventService.getAllEvents();
    }

    @GetMapping("/user/{userId}")
    public List<UserEvent> getEventsByUser(@PathVariable String userId) {
        return userEventService.getEventsByUser(userId);
    }

    @GetMapping("/type/{eventType}")
    public List<UserEvent> getEventsByType(@PathVariable String eventType) {
        return userEventService.getEventsByType(eventType);
    }

    @GetMapping("/count")
    public Long getCount() {
        return userEventService.getTotalCount();
    }

    @GetMapping("/avg")
    public Double getAvg() {
        return userEventService.getAverageValue();
    }
}
