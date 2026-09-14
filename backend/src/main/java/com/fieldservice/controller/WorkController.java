package com.fieldservice.controller;

import com.fieldservice.dto.StartWorkRequest;
import com.fieldservice.dto.WorkResponse;
import com.fieldservice.entity.UserEntity;
import jakarta.validation.Valid;
import com.fieldservice.security.SecurityUtils;
import com.fieldservice.service.WorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkController {

    private final WorkService workService;

    /**
     * GET /api/works/my
     * Returns works relevant to the authenticated user based on their role.
     */
    @GetMapping("/my")
    public ResponseEntity<List<WorkResponse>> getMyWorks() {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(workService.getMyWorks(currentUser));
    }

    /**
     * GET /api/works/{workId}
     * Returns a specific work. Access control enforced server-side.
     */
    @GetMapping("/{workId}")
    public ResponseEntity<WorkResponse> getWork(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(workService.getWork(workId, currentUser));
    }

    /**
     * POST /api/works/{workId}/start
     * Service Boy starts work. Verifies GPS location and records authoritative server timestamp.
     */
    @PostMapping("/{workId}/start")
    public ResponseEntity<WorkResponse> startWork(
            @PathVariable Long workId,
            @Valid @RequestBody StartWorkRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(workService.startWork(workId, request, currentUser));
    }

    /**
     * POST /api/works/{workId}/submit
     * Service Boy submits work for POC review.
     */
    @PostMapping("/{workId}/submit")
    public ResponseEntity<WorkResponse> submitForReview(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(workService.submitForReview(workId, currentUser));
    }

    /**
     * POST /api/works/{workId}/complete
     * Service Boy completes work after both approvals.
     */
    @PostMapping("/{workId}/complete")
    public ResponseEntity<WorkResponse> completeWork(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(workService.completeWork(workId, currentUser));
    }

    /**
     * POST /api/works/{workId}/resume
     * Service Boy resumes work after rejection.
     */
    @PostMapping("/{workId}/resume")
    public ResponseEntity<WorkResponse> resumeWork(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(workService.resumeWork(workId, currentUser));
    }
}
