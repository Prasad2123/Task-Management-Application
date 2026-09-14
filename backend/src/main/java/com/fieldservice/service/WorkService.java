package com.fieldservice.service;

import com.fieldservice.dto.StartWorkRequest;
import com.fieldservice.dto.WorkResponse;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.InvalidStateTransitionException;
import com.fieldservice.exception.LocationVerificationException;
import com.fieldservice.exception.ResourceNotFoundException;
import com.fieldservice.mapper.EntityMapper;
import com.fieldservice.repository.ApprovalRepository;
import com.fieldservice.repository.WorkRepository;
import com.fieldservice.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkService {

    private final WorkRepository workRepository;
    private final ActivityEventService activityEventService;
    private final NotificationService notificationService;
    private final ApprovalRepository approvalRepository;
    private final WorkReportService workReportService;

    @org.springframework.beans.factory.annotation.Autowired
    public WorkService(
            WorkRepository workRepository,
            ActivityEventService activityEventService,
            NotificationService notificationService,
            ApprovalRepository approvalRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) WorkReportService workReportService) {
        this.workRepository = workRepository;
        this.activityEventService = activityEventService;
        this.notificationService = notificationService;
        this.approvalRepository = approvalRepository;
        this.workReportService = workReportService;
    }

    public WorkService(
            WorkRepository workRepository,
            ActivityEventService activityEventService,
            NotificationService notificationService,
            ApprovalRepository approvalRepository) {
        this(workRepository, activityEventService, notificationService, approvalRepository, null);
    }

    /**
     * Get all works relevant to the authenticated user based on their role.
     * Service boy sees only his work.
     * POC sees only their assigned works.
     * Supervisor sees only their assigned works.
     */
    @Transactional(readOnly = true)
    public List<WorkResponse> getMyWorks(UserEntity currentUser) {
        List<WorkEntity> works = switch (currentUser.getRole()) {
            case SERVICE_BOY -> workRepository.findByServiceBoyId(currentUser.getId());
            case POC -> workRepository.findByPocId(currentUser.getId());
            case SITE_SUPERVISOR -> workRepository.findBySupervisorId(currentUser.getId());
        };
        return works.stream().map(EntityMapper::toWorkResponse).collect(Collectors.toList());
    }

    /**
     * Get a specific work by ID.
     * Access control: user must be service boy, POC, or supervisor of this work.
     */
    @Transactional(readOnly = true)
    public WorkResponse getWork(Long workId, UserEntity currentUser) {
        WorkEntity work = findWorkById(workId);
        verifyAccess(work, currentUser);
        return EntityMapper.toWorkResponse(work);
    }

    /**
     * Service Boy starts work.
     * Validates GPS location server-side against authoritative work coordinates and allowed radius.
     * Generates authoritative server start timestamp (Instant.now()).
     * Records verified location in WORK_STARTED activity event.
     * Valid transition: ASSIGNED → IN_PROGRESS
     */
    @Transactional
    public WorkResponse startWork(Long workId, StartWorkRequest request, UserEntity currentUser) {
        validateRole(currentUser, UserRole.SERVICE_BOY);
        WorkEntity work = findWorkById(workId);
        verifyServiceBoyOwnership(work, currentUser);

        if (work.getStatus() != WorkStatus.ASSIGNED) {
            throw new InvalidStateTransitionException(
                "Work cannot be started. Current status: " + work.getStatus());
        }

        // 1. Coordinate validity check
        if (request == null || !GeoUtils.isValidCoordinate(request.getLatitude(), request.getLongitude())) {
            throw new LocationVerificationException("Invalid GPS coordinates provided.");
        }

        // 2. Accuracy check (reject poor accuracy if accuracy is provided and exceeds threshold)
        if (request.getAccuracyMeters() != null && request.getAccuracyMeters() > GeoUtils.DEFAULT_MAX_ACCURACY_THRESHOLD_METERS) {
            throw new LocationVerificationException(
                String.format("GPS accuracy is too low (±%.1fm). A better GPS fix is required (must be within %.0fm).",
                    request.getAccuracyMeters(), GeoUtils.DEFAULT_MAX_ACCURACY_THRESHOLD_METERS));
        }

        // 3. Authoritative work location check
        if (work.getLatitude() == null || work.getLongitude() == null) {
            throw new LocationVerificationException("Authoritative work site location is not configured for this work.");
        }

        double allowedRadius = work.getAllowedRadiusMeters() != null ? work.getAllowedRadiusMeters() : 150.0;
        double distance = GeoUtils.calculateDistanceMeters(
            request.getLatitude(), request.getLongitude(),
            work.getLatitude(), work.getLongitude()
        );

        // 4. Geofence enforcement server-side
        if (distance > allowedRadius) {
            throw new LocationVerificationException(
                String.format("You are outside the permitted work location. Distance: %.0fm, Allowed radius: %.0fm.",
                    distance, allowedRadius));
        }

        // 5. Authoritative server start timestamp
        Instant now = Instant.now();
        work.setStatus(WorkStatus.IN_PROGRESS);
        work.setStartTime(now);
        workRepository.save(work);

        // 6. Record WORK_STARTED with verified GPS location
        activityEventService.record(
            work,
            ActivityEventType.WORK_STARTED,
            String.format("Work started at verified location (%.4f, %.4f, distance: %.0fm) by %s",
                request.getLatitude(), request.getLongitude(), distance, currentUser.getName()),
            currentUser,
            request.getLatitude(),
            request.getLongitude(),
            request.getAccuracyMeters()
        );

        log.info("Work {} started by service boy {} at location ({}, {}), distance: {}m",
            workId, currentUser.getId(), request.getLatitude(), request.getLongitude(), distance);

        WorkResponse response = EntityMapper.toWorkResponse(work);
        response.setDistanceFromWorkMeters(distance);
        response.setLocationVerified(true);
        return response;
    }

    /**
     * Service Boy submits work for POC review.
     * Valid transition: IN_PROGRESS → SUBMITTED_FOR_REVIEW
     */
    @Transactional
    public WorkResponse submitForReview(Long workId, UserEntity currentUser) {
        validateRole(currentUser, UserRole.SERVICE_BOY);
        WorkEntity work = findWorkById(workId);
        verifyServiceBoyOwnership(work, currentUser);

        if (work.getStatus() != WorkStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(
                "Work must be IN_PROGRESS to submit for review. Current: " + work.getStatus());
        }

        Instant now = Instant.now();
        work.setStatus(WorkStatus.SUBMITTED_FOR_REVIEW);
        work.setSubmittedAt(now);
        workRepository.save(work);

        // Reset previous approvals if this is a re-submission after rework
        List<ApprovalEntity> previousApprovals = approvalRepository.findByWorkIdOrderByCreatedAtAsc(workId);
        for (ApprovalEntity app : previousApprovals) {
            app.setStatus(ApprovalStatus.PENDING);
            app.setDecidedAt(null);
            app.setRejectionReason(null);
            approvalRepository.save(app);
        }

        activityEventService.record(work, ActivityEventType.WORK_SUBMITTED,
                "Work submitted for review by " + currentUser.getName(), currentUser);

        // Notify POC that work is submitted for their review
        notificationService.createNotification(
                work.getPoc(),
                work,
                NotificationType.WORK_SUBMITTED,
                "Work Submitted for Review",
                "Service Boy " + currentUser.getName() + " submitted " + work.getTitle() + " for your review."
        );

        return EntityMapper.toWorkResponse(work);
    }

    /**
     * Service Boy completes work after both approvals.
     * Valid transition: SUPERVISOR_APPROVED → COMPLETED
     * Validates: POC approval exists, Supervisor approval exists.
     */
    @Transactional
    public WorkResponse completeWork(Long workId, UserEntity currentUser) {
        validateRole(currentUser, UserRole.SERVICE_BOY);
        WorkEntity work = findWorkById(workId);
        verifyServiceBoyOwnership(work, currentUser);

        // Idempotency: If already completed, return existing work response idempotently
        if (work.getStatus() == WorkStatus.COMPLETED) {
            log.info("Work {} is already COMPLETED, returning existing state idempotently", workId);
            return EntityMapper.toWorkResponse(work);
        }

        if (work.getStatus() != WorkStatus.SUPERVISOR_APPROVED) {
            throw new InvalidStateTransitionException(
                "Work requires Supervisor approval before completion. Current: " + work.getStatus());
        }

        Instant now = Instant.now();
        work.setStatus(WorkStatus.COMPLETED);
        work.setCompletedAt(now);
        workRepository.save(work);

        activityEventService.record(work, ActivityEventType.WORK_COMPLETED,
                "Work completed by " + currentUser.getName(), currentUser);

        // Authoritative Report Generation
        if (workReportService != null) {
            try {
                workReportService.generateReport(work);
            } catch (Exception e) {
                log.error("Failed to generate work report for work {}: {}", workId, e.getMessage(), e);
            }
        }

        // Notify ALL THREE stakeholders of final completion & report availability
        String completionMsg = "Work completed — final report is available for " + work.getTitle() + ".";
        if (work.getServiceBoy() != null) {
            notificationService.createNotification(
                    work.getServiceBoy(),
                    work,
                    NotificationType.WORK_COMPLETED,
                    "Work Completed",
                    completionMsg
            );
        }
        if (work.getPoc() != null) {
            notificationService.createNotification(
                    work.getPoc(),
                    work,
                    NotificationType.WORK_COMPLETED,
                    "Work Completed",
                    completionMsg
            );
        }
        if (work.getSupervisor() != null) {
            notificationService.createNotification(
                    work.getSupervisor(),
                    work,
                    NotificationType.WORK_COMPLETED,
                    "Work Completed",
                    completionMsg
            );
        }

        log.info("Work {} completed by service boy {}, all 3 stakeholders notified", workId, currentUser.getId());
        return EntityMapper.toWorkResponse(work);
    }

    /**
     * Resume work after rejection (back to IN_PROGRESS).
     */
    @Transactional
    public WorkResponse resumeWork(Long workId, UserEntity currentUser) {
        validateRole(currentUser, UserRole.SERVICE_BOY);
        WorkEntity work = findWorkById(workId);
        verifyServiceBoyOwnership(work, currentUser);

        if (work.getStatus() != WorkStatus.REJECTED) {
            throw new InvalidStateTransitionException(
                "Work is not in REJECTED state. Current: " + work.getStatus());
        }

        work.setStatus(WorkStatus.IN_PROGRESS);
        workRepository.save(work);

        activityEventService.record(work, ActivityEventType.WORK_RESUMED,
                "Work resumed after rejection by " + currentUser.getName(), currentUser);

        notificationService.createNotification(
                work.getPoc(),
                work,
                NotificationType.WORK_RESUMED,
                "Work Resumed",
                "Service Boy " + currentUser.getName() + " has resumed work on " + work.getTitle() + " to address review feedback."
        );

        return EntityMapper.toWorkResponse(work);
    }

    // ---- Internal helpers ----

    public WorkEntity findWorkById(Long workId) {
        return workRepository.findById(workId)
                .orElseThrow(() -> new ResourceNotFoundException("Work not found: " + workId));
    }

    public WorkEntity saveWork(WorkEntity work) {
        return workRepository.save(work);
    }

    public void verifyAccess(WorkEntity work, UserEntity user) {
        boolean hasAccess = switch (user.getRole()) {
            case SERVICE_BOY -> work.getServiceBoy().getId().equals(user.getId());
            case POC -> work.getPoc().getId().equals(user.getId());
            case SITE_SUPERVISOR -> work.getSupervisor().getId().equals(user.getId());
        };
        if (!hasAccess) {
            throw new AccessDeniedException("You do not have access to this work");
        }
    }

    private void verifyServiceBoyOwnership(WorkEntity work, UserEntity user) {
        if (!work.getServiceBoy().getId().equals(user.getId())) {
            throw new AccessDeniedException("This work does not belong to you");
        }
    }

    private void validateRole(UserEntity user, UserRole required) {
        if (user.getRole() != required) {
            throw new AccessDeniedException("Only " + required.name() + " can perform this action");
        }
    }
}
