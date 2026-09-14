package com.fieldservice.controller;

import com.fieldservice.dto.ActivityEventResponse;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.mapper.EntityMapper;
import com.fieldservice.repository.ActivityEventRepository;
import com.fieldservice.security.SecurityUtils;
import com.fieldservice.service.WorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/works/{workId}/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityEventRepository activityEventRepository;
    private final WorkService workService;

    /** GET /api/works/{workId}/activity */
    @GetMapping
    public ResponseEntity<List<ActivityEventResponse>> getActivity(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        workService.verifyAccess(workService.findWorkById(workId), currentUser);

        List<ActivityEventResponse> events = activityEventRepository
                .findByWorkIdOrderByEventTimestampAsc(workId)
                .stream()
                .map(EntityMapper::toActivityEventResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }
}
