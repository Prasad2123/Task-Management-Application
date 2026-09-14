package com.fieldservice.controller;

import com.fieldservice.dto.ApprovalRequest;
import com.fieldservice.dto.ApprovalResponse;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.security.SecurityUtils;
import com.fieldservice.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    /** GET /api/works/{workId}/approvals */
    @GetMapping("/{workId}/approvals")
    public ResponseEntity<List<ApprovalResponse>> getApprovals(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(approvalService.getApprovals(workId, currentUser));
    }

    /**
     * Unified POC approval decision endpoint.
     * POST /api/works/{workId}/approvals/poc
     * Body: { "decision": "APPROVED" } or { "decision": "REJECTED", "reason": "..." }
     */
    @PostMapping("/{workId}/approvals/poc")
    public ResponseEntity<ApprovalResponse> pocDecision(
            @PathVariable Long workId,
            @RequestBody(required = false) ApprovalRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(approvalService.pocDecision(workId, request, currentUser));
    }

    /**
     * Unified Supervisor approval decision endpoint.
     * POST /api/works/{workId}/approvals/supervisor
     * Body: { "decision": "APPROVED" } or { "decision": "REJECTED", "reason": "..." }
     */
    @PostMapping("/{workId}/approvals/supervisor")
    public ResponseEntity<ApprovalResponse> supervisorDecision(
            @PathVariable Long workId,
            @RequestBody(required = false) ApprovalRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(approvalService.supervisorDecision(workId, request, currentUser));
    }

    /** Legacy / specific endpoints retained for backwards compatibility */

    /** POST /api/works/{workId}/poc/approve */
    @PostMapping("/{workId}/poc/approve")
    public ResponseEntity<ApprovalResponse> pocApprove(
            @PathVariable Long workId,
            @RequestBody(required = false) ApprovalRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(approvalService.pocApprove(workId, request, currentUser));
    }

    /** POST /api/works/{workId}/poc/reject */
    @PostMapping("/{workId}/poc/reject")
    public ResponseEntity<ApprovalResponse> pocReject(
            @PathVariable Long workId,
            @RequestBody(required = false) ApprovalRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(approvalService.pocReject(workId, request, currentUser));
    }

    /** POST /api/works/{workId}/supervisor/approve */
    @PostMapping("/{workId}/supervisor/approve")
    public ResponseEntity<ApprovalResponse> supervisorApprove(
            @PathVariable Long workId,
            @RequestBody(required = false) ApprovalRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(approvalService.supervisorApprove(workId, request, currentUser));
    }

    /** POST /api/works/{workId}/supervisor/reject */
    @PostMapping("/{workId}/supervisor/reject")
    public ResponseEntity<ApprovalResponse> supervisorReject(
            @PathVariable Long workId,
            @RequestBody(required = false) ApprovalRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(approvalService.supervisorReject(workId, request, currentUser));
    }
}
