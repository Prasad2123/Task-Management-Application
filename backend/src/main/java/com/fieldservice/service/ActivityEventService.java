package com.fieldservice.service;

import com.fieldservice.entity.*;
import com.fieldservice.repository.ActivityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Records activity events for the work timeline.
 * All timestamps are authoritative server-side Instants.
 */
@Service
@RequiredArgsConstructor
public class ActivityEventService {

    private final ActivityEventRepository activityEventRepository;

    @Transactional
    public ActivityEventEntity record(WorkEntity work,
                                       ActivityEventType type,
                                       String description,
                                       UserEntity performedBy) {
        return record(work, type, description, performedBy, null, null, null);
    }

    @Transactional
    public ActivityEventEntity record(WorkEntity work,
                                       ActivityEventType type,
                                       String description,
                                       UserEntity performedBy,
                                       Double latitude,
                                       Double longitude,
                                       Double accuracyMeters) {
        ActivityEventEntity event = ActivityEventEntity.builder()
                .work(work)
                .eventType(type)
                .description(description)
                .performedBy(performedBy)
                .latitude(latitude)
                .longitude(longitude)
                .accuracyMeters(accuracyMeters)
                .eventTimestamp(Instant.now())
                .build();
        return activityEventRepository.save(event);
    }
}
