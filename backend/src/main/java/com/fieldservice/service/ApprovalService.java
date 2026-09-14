package com.fieldservice.service;

import com.fieldservice.dto.ApprovalRequest;
import com.fieldservice.dto.ApprovalResponse;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.BadRequestException;
import com.fieldservice.exception.ConflictException;
import com.fieldservice.exception.InvalidStateTransitionException;
import com.fieldservice.mapper.EntityMapper;
import com.fieldservice.repository.ApprovalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final WorkService workService;
    private final ActivityEventService activityEventService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ApprovalResponse> getApprovals(Long workId, UserEntity currentUser) {
        WorkEntity work = workService.findWorkById(workId);
        workService.verifyAccess(work, currentUser);
        return approvalRepository.findByWorkIdOrderByCreatedAtAsc(workId)
                .stream().map(EntityMapper::toApprovalResponse).collect(Collectors.toList());
    }

    // ---- Unified Endpoints Support ----

    @Transactional
    public ApprovalResponse pocDecision(Long workId, ApprovalRequest request, UserEntity currentUser) {
        if (request != null && "REJECTED".equalsIgnoreCase(request.getDecision())) {
            return pocReject(workId, request, currentUser);
        }
        return pocApprove(workId, request, currentUser);
    }

    @Transactional
    public ApprovalResponse supervisorDecision(Long workId, ApprovalRequest request, UserEntity currentUser) {
        if (request != null && "REJECTED".equalsIgnoreCase(request.getDecision())) {
            return supervisorReject(workId, request, currentUser);
        }
        return supervisorApprove(workId, request, currentUser);
    }

    // ---- POC Approval ----

    @Transactional
    public ApprovalResponse pocApprove(Long workId, ApprovalRequest request, UserEntity currentUser) {
        requireRole(currentUser, UserRole.POC);
        WorkEntity work = workService.findWorkById(workId);

        if (!work.getPoc().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not the POC for this work");
        }

        if (work.getStatus() != WorkStatus.SUBMITTED_FOR_REVIEW) {
            throw new InvalidStateTransitionException(
                "Work must be SUBMITTED_FOR_REVIEW for POC approval. Current: " + work.getStatus());
        }

        ApprovalEntity approval = getOrCreateApproval(work, currentUser, UserRole.POC);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            throw new ConflictException("POC has already approved this work");
        }
        if (approval.getStatus() == ApprovalStatus.REJECTED) {
            throw new ConflictException("POC has already rejected this work");
        }

        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setDecidedAt(now);
        approval.setRejectionReason(null);
        approvalRepository.save(approval);

        work.setStatus(WorkStatus.POC_APPROVED);
        workService.saveWork(work);

        activityEventService.record(work, ActivityEventType.POC_APPROVED,
                "Work approved by POC: " + currentUser.getName(), currentUser);

        // Notify Service Boy
        notificationService.createNotification(
                work.getServiceBoy(),
                work,
                NotificationType.POC_APPROVED,
                "Work Evidence Approved",
                currentUser.getName() + " approved the work evidence for " + work.getTitle() + ". Awaiting final Supervisor sign-off."
        );

        // Notify Supervisor that work is now ready for supervisor review
        notificationService.createNotification(
                work.getSupervisor(),
                work,
                NotificationType.POC_APPROVED,
                "Review Required: " + work.getTitle(),
                currentUser.getName() + " has approved work evidence. Please complete supervisor review."
        );

        return EntityMapper.toApprovalResponse(approval);
    }

    @Transactional
    public ApprovalResponse pocReject(Long workId, ApprovalRequest request, UserEntity currentUser) {
        requireRole(currentUser, UserRole.POC);
        WorkEntity work = workService.findWorkById(workId);

        if (!work.getPoc().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not the POC for this work");
        }

        if (work.getStatus() != WorkStatus.SUBMITTED_FOR_REVIEW) {
            throw new InvalidStateTransitionException(
                "Work must be SUBMITTED_FOR_REVIEW to reject. Current: " + work.getStatus());
        }

        String reason = validateAndCleanReason(request);

        ApprovalEntity approval = getOrCreateApproval(work, currentUser, UserRole.POC);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            throw new ConflictException("POC has already approved this work");
        }
        if (approval.getStatus() == ApprovalStatus.REJECTED) {
            throw new ConflictException("POC has already rejected this work");
        }

        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setDecidedAt(now);
        approval.setRejectionReason(reason);
        approvalRepository.save(approval);

        work.setStatus(WorkStatus.REJECTED);
        workService.saveWork(work);

        activityEventService.record(work, ActivityEventType.POC_REJECTED,
                "Work rejected by POC: " + currentUser.getName() + " - " + reason, currentUser);

        // Notify Service Boy with rejection reason
        notificationService.createNotification(
                work.getServiceBoy(),
                work,
                NotificationType.POC_REJECTED,
                "Changes Requested by POC",
                currentUser.getName() + " requested revisions on " + work.getTitle() + ": \"" + reason + "\""
        );

        return EntityMapper.toApprovalResponse(approval);
    }

    // ---- Supervisor Approval ----

    @Transactional
    public ApprovalResponse supervisorApprove(Long workId, ApprovalRequest request,
                                               UserEntity currentUser) {
        requireRole(currentUser, UserRole.SITE_SUPERVISOR);
        WorkEntity work = workService.findWorkById(workId);

        if (!work.getSupervisor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not the Supervisor for this work");
        }

        // CRITICAL RULE: Must have POC approval first
        verifyPocApprovalOrThrow(work);

        ApprovalEntity approval = getOrCreateApproval(work, currentUser, UserRole.SITE_SUPERVISOR);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            throw new ConflictException("Supervisor has already approved this work");
        }
        if (approval.getStatus() == ApprovalStatus.REJECTED) {
            throw new ConflictException("Supervisor has already rejected this work");
        }

        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setDecidedAt(now);
        approval.setRejectionReason(null);
        approvalRepository.save(approval);

        work.setStatus(WorkStatus.SUPERVISOR_APPROVED);
        workService.saveWork(work);

        activityEventService.record(work, ActivityEventType.SUPERVISOR_APPROVED,
                "Work approved by Supervisor: " + currentUser.getName(), currentUser);

        // Notify Service Boy: Job ready for completion
        notificationService.createNotification(
                work.getServiceBoy(),
                work,
                NotificationType.SUPERVISOR_APPROVED,
                "Work Approved — Ready for Completion",
                "Supervisor " + currentUser.getName() + " gave final approval for " + work.getTitle() + ". You may now complete the job."
        );

        // Notify POC that final review is complete
        notificationService.createNotification(
                work.getPoc(),
                work,
                NotificationType.SUPERVISOR_APPROVED,
                "Supervisor Review Completed",
                "Supervisor " + currentUser.getName() + " gave final approval for " + work.getTitle() + "."
        );

        return EntityMapper.toApprovalResponse(approval);
    }

    @Transactional
    public ApprovalResponse supervisorReject(Long workId, ApprovalRequest request,
                                              UserEntity currentUser) {
        requireRole(currentUser, UserRole.SITE_SUPERVISOR);
        WorkEntity work = workService.findWorkById(workId);

        if (!work.getSupervisor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not the Supervisor for this work");
        }

        // CRITICAL RULE: Must have POC approval first before Supervisor can act
        verifyPocApprovalOrThrow(work);

        String reason = validateAndCleanReason(request);

        ApprovalEntity approval = getOrCreateApproval(work, currentUser, UserRole.SITE_SUPERVISOR);
        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            throw new ConflictException("Supervisor has already approved this work");
        }
        if (approval.getStatus() == ApprovalStatus.REJECTED) {
            throw new ConflictException("Supervisor has already rejected this work");
        }

        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setDecidedAt(now);
        approval.setRejectionReason(reason);
        approvalRepository.save(approval);

        work.setStatus(WorkStatus.REJECTED);
        workService.saveWork(work);

        activityEventService.record(work, ActivityEventType.SUPERVISOR_REJECTED,
                "Work rejected by Supervisor: " + currentUser.getName() + " - " + reason, currentUser);

        // Notify Service Boy with rejection reason
        notificationService.createNotification(
                work.getServiceBoy(),
                work,
                NotificationType.SUPERVISOR_REJECTED,
                "Changes Requested by Supervisor",
                "Supervisor " + currentUser.getName() + " requested revisions on " + work.getTitle() + ": \"" + reason + "\""
        );

        return EntityMapper.toApprovalResponse(approval);
    }

    // ---- Rework Support: Reset Approvals ----

    @Transactional
    public void resetApprovalsForRework(Long workId) {
        List<ApprovalEntity> approvals = approvalRepository.findByWorkIdOrderByCreatedAtAsc(workId);
        for (ApprovalEntity approval : approvals) {
            approval.setStatus(ApprovalStatus.PENDING);
            approval.setDecidedAt(null);
            approval.setRejectionReason(null);
            approvalRepository.save(approval);
        }
        log.info("Reset {} approvals to PENDING for rework on work {}", approvals.size(), workId);
    }

    // ---- Helpers ----

    private void verifyPocApprovalOrThrow(WorkEntity work) {
        Optional<ApprovalEntity> pocApproval = approvalRepository.findByWorkIdAndApproverRole(work.getId(), UserRole.POC);
        boolean pocIsApproved = pocApproval.isPresent() && pocApproval.get().getStatus() == ApprovalStatus.APPROVED;
        if (!pocIsApproved || work.getStatus() != WorkStatus.POC_APPROVED) {
            throw new InvalidStateTransitionException(
                "Supervisor approval is unavailable until POC approval is completed.");
        }
    }

    private String validateAndCleanReason(ApprovalRequest request) {
        if (request == null || request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BadRequestException("Rejection reason is mandatory");
        }
        return request.getReason().trim();
    }

    private ApprovalEntity getOrCreateApproval(WorkEntity work, UserEntity approver, UserRole role) {
        return approvalRepository.findByWorkIdAndApproverRole(work.getId(), role)
                .orElseGet(() -> ApprovalEntity.builder()
                        .work(work)
                        .approver(approver)
                        .approverRole(role)
                        .status(ApprovalStatus.PENDING)
                        .build());
    }

    private void requireRole(UserEntity user, UserRole required) {
        if (user.getRole() != required) {
            throw new AccessDeniedException("Only " + required.name() + " can perform this action");
        }
    }
}
