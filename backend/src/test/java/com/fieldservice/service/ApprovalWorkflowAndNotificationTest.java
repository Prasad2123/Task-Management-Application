package com.fieldservice.service;

import com.fieldservice.dto.ApprovalRequest;
import com.fieldservice.dto.ApprovalResponse;
import com.fieldservice.dto.NotificationResponse;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.BadRequestException;
import com.fieldservice.exception.ConflictException;
import com.fieldservice.exception.InvalidStateTransitionException;
import com.fieldservice.notification.NotificationGateway;
import com.fieldservice.repository.ApprovalRepository;
import com.fieldservice.repository.NotificationRepository;
import com.fieldservice.repository.WorkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalWorkflowAndNotificationTest {

    @Mock
    private ApprovalRepository approvalRepository;

    @Mock
    private WorkRepository workRepository;

    @Mock
    private WorkService workService;

    @Mock
    private ActivityEventService activityEventService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationGateway notificationGateway;

    private NotificationService notificationService;
    private ApprovalService approvalService;

    private UserEntity serviceBoy;
    private UserEntity poc;
    private UserEntity supervisor;
    private UserEntity unauthorizedUser;
    private WorkEntity work;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationGateway);
        approvalService = new ApprovalService(approvalRepository, workService, activityEventService, notificationService);

        lenient().when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(inv -> {
            NotificationEntity n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId(500L);
            }
            return n;
        });

        serviceBoy = UserEntity.builder()
                .id(1L).name("Rahul Patil").email("service@demo.com")
                .role(UserRole.SERVICE_BOY).active(true).build();

        poc = UserEntity.builder()
                .id(2L).name("Amit Kumar").email("poc@demo.com")
                .role(UserRole.POC).active(true).build();

        supervisor = UserEntity.builder()
                .id(3L).name("Suresh Mehta").email("supervisor@demo.com")
                .role(UserRole.SITE_SUPERVISOR).active(true).build();

        unauthorizedUser = UserEntity.builder()
                .id(99L).name("Intruder").email("intruder@demo.com")
                .role(UserRole.POC).active(true).build();

        work = WorkEntity.builder()
                .id(100L)
                .title("Monthly Pest Control")
                .status(WorkStatus.SUBMITTED_FOR_REVIEW)
                .serviceBoy(serviceBoy)
                .poc(poc)
                .supervisor(supervisor)
                .build();
    }

    // ── 1. POC Approval & Rejection ──

    @Test
    @DisplayName("1. POC approval succeeds and transitions work to POC_APPROVED")
    void testPocApprove_succeeds() {
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.empty());
        when(approvalRepository.save(any(ApprovalEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(inv -> {
            NotificationEntity n = inv.getArgument(0);
            n.setId(501L);
            return n;
        });

        ApprovalResponse response = approvalService.pocApprove(100L, new ApprovalRequest(), poc);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(work.getStatus()).isEqualTo(WorkStatus.POC_APPROVED);
        verify(workService).saveWork(work);
    }

    @Test
    @DisplayName("2. POC rejection succeeds and transitions work to REJECTED")
    void testPocReject_succeeds() {
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.empty());
        when(approvalRepository.save(any(ApprovalEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(inv -> {
            NotificationEntity n = inv.getArgument(0);
            n.setId(502L);
            return n;
        });

        ApprovalRequest request = new ApprovalRequest("Perimeter treatment missing");
        ApprovalResponse response = approvalService.pocReject(100L, request, poc);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectionReason()).isEqualTo("Perimeter treatment missing");
        assertThat(work.getStatus()).isEqualTo(WorkStatus.REJECTED);
        verify(workService).saveWork(work);
    }

    @Test
    @DisplayName("3. Empty POC rejection reason throws BadRequestException")
    void testPocReject_emptyReason_throwsBadRequest() {
        when(workService.findWorkById(100L)).thenReturn(work);

        assertThatThrownBy(() -> approvalService.pocReject(100L, new ApprovalRequest("   "), poc))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Rejection reason is mandatory");

        assertThatThrownBy(() -> approvalService.pocReject(100L, new ApprovalRequest(null), poc))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("4. Unauthorized POC cannot approve another site's work")
    void testPocApprove_unauthorizedPoc_throwsAccessDenied() {
        when(workService.findWorkById(100L)).thenReturn(work);

        assertThatThrownBy(() -> approvalService.pocApprove(100L, new ApprovalRequest(), unauthorizedUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the POC for this work");
    }

    // ── 2. Supervisor Approval & Critical Ordering Rule ──

    @Test
    @DisplayName("5. Supervisor approval succeeds AFTER POC approval")
    void testSupervisorApprove_afterPocApproval_succeeds() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.POC).build();

        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.SITE_SUPERVISOR)).thenReturn(Optional.empty());
        when(approvalRepository.save(any(ApprovalEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(inv -> {
            NotificationEntity n = inv.getArgument(0);
            n.setId(503L);
            return n;
        });

        ApprovalResponse response = approvalService.supervisorApprove(100L, new ApprovalRequest(), supervisor);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(work.getStatus()).isEqualTo(WorkStatus.SUPERVISOR_APPROVED);
        verify(workService).saveWork(work);
    }

    @Test
    @DisplayName("6. Supervisor approval is REJECTED if POC has not approved yet")
    void testSupervisorApprove_beforePocApproval_throwsInvalidState() {
        work.setStatus(WorkStatus.SUBMITTED_FOR_REVIEW); // Not POC_APPROVED
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.supervisorApprove(100L, new ApprovalRequest(), supervisor))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Supervisor approval is unavailable until POC approval is completed.");
    }

    @Test
    @DisplayName("7. Supervisor approval is REJECTED if POC rejected the work")
    void testSupervisorApprove_whenPocRejected_throwsInvalidState() {
        work.setStatus(WorkStatus.REJECTED);
        ApprovalEntity pocApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.REJECTED)
                .approverRole(UserRole.POC).build();

        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));

        assertThatThrownBy(() -> approvalService.supervisorApprove(100L, new ApprovalRequest(), supervisor))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Supervisor approval is unavailable until POC approval is completed.");
    }

    @Test
    @DisplayName("8. Supervisor rejection succeeds after POC approval")
    void testSupervisorReject_afterPocApproval_succeeds() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.POC).build();

        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.SITE_SUPERVISOR)).thenReturn(Optional.empty());
        when(approvalRepository.save(any(ApprovalEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(inv -> {
            NotificationEntity n = inv.getArgument(0);
            n.setId(504L);
            return n;
        });

        ApprovalRequest request = new ApprovalRequest("Safety goggles were not worn in photos");
        ApprovalResponse response = approvalService.supervisorReject(100L, request, supervisor);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectionReason()).isEqualTo("Safety goggles were not worn in photos");
        assertThat(work.getStatus()).isEqualTo(WorkStatus.REJECTED);
        verify(workService).saveWork(work);
    }

    @Test
    @DisplayName("9. Supervisor rejection with empty reason throws BadRequestException")
    void testSupervisorReject_emptyReason_throwsBadRequest() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.POC).build();

        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));

        assertThatThrownBy(() -> approvalService.supervisorReject(100L, new ApprovalRequest("  "), supervisor))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Rejection reason is mandatory");
    }

    @Test
    @DisplayName("10. Unauthorized Supervisor cannot approve work")
    void testSupervisorApprove_unauthorizedSupervisor_throwsAccessDenied() {
        UserEntity otherSupervisor = UserEntity.builder().id(77L).role(UserRole.SITE_SUPERVISOR).build();
        when(workService.findWorkById(100L)).thenReturn(work);

        assertThatThrownBy(() -> approvalService.supervisorApprove(100L, new ApprovalRequest(), otherSupervisor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the Supervisor for this work");
    }

    // ── 3. Duplicate Prevention & Authoritative Timestamps ──

    @Test
    @DisplayName("11. Duplicate POC approval is prevented with ConflictException")
    void testPocApprove_whenAlreadyApproved_throwsConflict() {
        when(workService.findWorkById(100L)).thenReturn(work);
        ApprovalEntity existing = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.POC).build();
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> approvalService.pocApprove(100L, new ApprovalRequest(), poc))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("POC has already approved this work");
    }

    @Test
    @DisplayName("12. Duplicate Supervisor approval is prevented with ConflictException")
    void testSupervisorApprove_whenAlreadyApproved_throwsConflict() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.POC).build();
        ApprovalEntity supervisorApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.SITE_SUPERVISOR).build();

        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.SITE_SUPERVISOR)).thenReturn(Optional.of(supervisorApproval));

        assertThatThrownBy(() -> approvalService.supervisorApprove(100L, new ApprovalRequest(), supervisor))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Supervisor has already approved this work");
    }

    @Test
    @DisplayName("13. Approval timestamps are generated server-side")
    void testApprovalTimestamp_serverGenerated() {
        Instant before = Instant.now().minusSeconds(1);

        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.empty());
        when(approvalRepository.save(any(ApprovalEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalResponse response = approvalService.pocApprove(100L, new ApprovalRequest(), poc);

        Instant after = Instant.now().plusSeconds(1);
        assertThat(response.getDecidedAt()).isAfterOrEqualTo(before);
        assertThat(response.getDecidedAt()).isBeforeOrEqualTo(after);
    }

    // ── 4. Audit Trail Logging ──

    @Test
    @DisplayName("14. POC approval logs exactly one POC_APPROVED activity event")
    void testPocApprove_logsActivityEvent() {
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.pocApprove(100L, new ApprovalRequest(), poc);

        verify(activityEventService, times(1))
                .record(eq(work), eq(ActivityEventType.POC_APPROVED), contains("Amit Kumar"), eq(poc));
    }

    @Test
    @DisplayName("15. POC rejection logs exactly one POC_REJECTED activity event with reason")
    void testPocReject_logsActivityEvent() {
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.pocReject(100L, new ApprovalRequest("Chemical batch number missing"), poc);

        verify(activityEventService, times(1))
                .record(eq(work), eq(ActivityEventType.POC_REJECTED), contains("Chemical batch number missing"), eq(poc));
    }

    @Test
    @DisplayName("16. Supervisor approval logs exactly one SUPERVISOR_APPROVED event")
    void testSupervisorApprove_logsActivityEvent() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder().status(ApprovalStatus.APPROVED).build();
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.SITE_SUPERVISOR)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.supervisorApprove(100L, new ApprovalRequest(), supervisor);

        verify(activityEventService, times(1))
                .record(eq(work), eq(ActivityEventType.SUPERVISOR_APPROVED), contains("Suresh Mehta"), eq(supervisor));
    }

    @Test
    @DisplayName("17. Supervisor rejection logs exactly one SUPERVISOR_REJECTED event")
    void testSupervisorReject_logsActivityEvent() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder().status(ApprovalStatus.APPROVED).build();
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.SITE_SUPERVISOR)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.supervisorReject(100L, new ApprovalRequest("PPE inspection photo blurred"), supervisor);

        verify(activityEventService, times(1))
                .record(eq(work), eq(ActivityEventType.SUPERVISOR_REJECTED), contains("PPE inspection photo blurred"), eq(supervisor));
    }

    // ── 5. Real Notifications Generation ──

    @Test
    @DisplayName("18. POC approval creates real notifications for Service Boy and Supervisor")
    void testPocApprove_createsNotificationsForServiceBoyAndSupervisor() {
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<NotificationEntity> notifCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        when(notificationRepository.save(notifCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.pocApprove(100L, new ApprovalRequest(), poc);

        List<NotificationEntity> savedNotifs = notifCaptor.getAllValues();
        assertThat(savedNotifs).hasSize(2);

        // Recipient 1: Service Boy
        NotificationEntity sbNotif = savedNotifs.stream().filter(n -> n.getUser().getId().equals(1L)).findFirst().orElseThrow();
        assertThat(sbNotif.getType()).isEqualTo(NotificationType.POC_APPROVED);
        assertThat(sbNotif.getTitle()).isEqualTo("Work Evidence Approved");

        // Recipient 2: Supervisor
        NotificationEntity supNotif = savedNotifs.stream().filter(n -> n.getUser().getId().equals(3L)).findFirst().orElseThrow();
        assertThat(supNotif.getType()).isEqualTo(NotificationType.POC_APPROVED);
        assertThat(supNotif.getMessage()).contains("approved work evidence");
    }

    @Test
    @DisplayName("19. POC rejection creates notification for Service Boy containing rejection reason")
    void testPocReject_createsNotificationForServiceBoyWithReason() {
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<NotificationEntity> notifCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        when(notificationRepository.save(notifCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.pocReject(100L, new ApprovalRequest("Trap 4 needs bait replacement"), poc);

        List<NotificationEntity> savedNotifs = notifCaptor.getAllValues();
        assertThat(savedNotifs).hasSize(1);
        NotificationEntity notif = savedNotifs.get(0);
        assertThat(notif.getUser().getId()).isEqualTo(1L);
        assertThat(notif.getType()).isEqualTo(NotificationType.POC_REJECTED);
        assertThat(notif.getMessage()).contains("Trap 4 needs bait replacement");
    }

    @Test
    @DisplayName("20. Supervisor approval creates notifications for Service Boy and POC")
    void testSupervisorApprove_createsNotificationsForServiceBoyAndPoc() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder().status(ApprovalStatus.APPROVED).build();
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.SITE_SUPERVISOR)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<NotificationEntity> notifCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        when(notificationRepository.save(notifCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.supervisorApprove(100L, new ApprovalRequest(), supervisor);

        List<NotificationEntity> savedNotifs = notifCaptor.getAllValues();
        assertThat(savedNotifs).hasSize(2);

        // Recipient 1: Service Boy
        NotificationEntity sbNotif = savedNotifs.stream().filter(n -> n.getUser().getId().equals(1L)).findFirst().orElseThrow();
        assertThat(sbNotif.getType()).isEqualTo(NotificationType.SUPERVISOR_APPROVED);
        assertThat(sbNotif.getTitle()).isEqualTo("Work Approved — Ready for Completion");

        // Recipient 2: POC
        NotificationEntity pocNotif = savedNotifs.stream().filter(n -> n.getUser().getId().equals(2L)).findFirst().orElseThrow();
        assertThat(pocNotif.getType()).isEqualTo(NotificationType.SUPERVISOR_APPROVED);
        assertThat(pocNotif.getTitle()).isEqualTo("Supervisor Review Completed");
    }

    @Test
    @DisplayName("21. Supervisor rejection creates notification for Service Boy with reason")
    void testSupervisorReject_createsNotificationForServiceBoyWithReason() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder().status(ApprovalStatus.APPROVED).build();
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.SITE_SUPERVISOR)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<NotificationEntity> notifCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        when(notificationRepository.save(notifCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.supervisorReject(100L, new ApprovalRequest("Entry gate logbook missing"), supervisor);

        List<NotificationEntity> savedNotifs = notifCaptor.getAllValues();
        assertThat(savedNotifs).hasSize(1);
        NotificationEntity notif = savedNotifs.get(0);
        assertThat(notif.getUser().getId()).isEqualTo(1L);
        assertThat(notif.getType()).isEqualTo(NotificationType.SUPERVISOR_REJECTED);
        assertThat(notif.getMessage()).contains("Entry gate logbook missing");
    }

    @Test
    @DisplayName("22. Notification recipients are determined from actual work relationships")
    void testNotificationRecipients_dynamicFromWorkRelationships() {
        UserEntity customServiceBoy = UserEntity.builder().id(55L).name("Karan").role(UserRole.SERVICE_BOY).build();
        UserEntity customPoc = UserEntity.builder().id(66L).name("Pooja").role(UserRole.POC).build();
        UserEntity customSupervisor = UserEntity.builder().id(77L).name("Vikas").role(UserRole.SITE_SUPERVISOR).build();

        WorkEntity dynamicWork = WorkEntity.builder()
                .id(200L).title("Custom Work")
                .status(WorkStatus.SUBMITTED_FOR_REVIEW)
                .serviceBoy(customServiceBoy).poc(customPoc).supervisor(customSupervisor)
                .build();

        when(workService.findWorkById(200L)).thenReturn(dynamicWork);
        when(approvalRepository.findByWorkIdAndApproverRole(200L, UserRole.POC)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<NotificationEntity> notifCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        when(notificationRepository.save(notifCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.pocApprove(200L, new ApprovalRequest(), customPoc);

        List<NotificationEntity> savedNotifs = notifCaptor.getAllValues();
        assertThat(savedNotifs).hasSize(2);
        assertThat(savedNotifs.get(0).getUser().getId()).isEqualTo(55L);
        assertThat(savedNotifs.get(1).getUser().getId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("23. Users cannot access another user's notifications")
    void testNotificationIsolation_usersOnlyAccessTheirOwn() {
        NotificationEntity sbNotif = NotificationEntity.builder()
                .id(101L).user(serviceBoy).type(NotificationType.POC_APPROVED).title("Service Alert").message("Msg").isRead(false).build();

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(sbNotif));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());

        List<NotificationResponse> sbList = notificationService.getMyNotifications(serviceBoy);
        List<NotificationResponse> pocList = notificationService.getMyNotifications(poc);

        assertThat(sbList).hasSize(1);
        assertThat(sbList.get(0).getId()).isEqualTo(101L);
        assertThat(pocList).isEmpty();
    }

    // ── 6. Rework and Ready for Completion ──

    @Test
    @DisplayName("24. Rework reset restores approvals to PENDING state")
    void testRework_resetsApprovalsToPending() {
        ApprovalEntity pocApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .decidedAt(Instant.now())
                .approverRole(UserRole.POC).build();
        ApprovalEntity supApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.REJECTED)
                .decidedAt(Instant.now())
                .rejectionReason("Fix needed")
                .approverRole(UserRole.SITE_SUPERVISOR).build();

        when(approvalRepository.findByWorkIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(pocApproval, supApproval));

        approvalService.resetApprovalsForRework(100L);

        assertThat(pocApproval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(pocApproval.getDecidedAt()).isNull();
        assertThat(supApproval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(supApproval.getDecidedAt()).isNull();
        assertThat(supApproval.getRejectionReason()).isNull();

        verify(approvalRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("25. Both approvals complete marks work state as SUPERVISOR_APPROVED (ready for completion)")
    void testBothApprovals_resultsInReadyForCompletion() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder().status(ApprovalStatus.APPROVED).build();
        when(workService.findWorkById(100L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC)).thenReturn(Optional.of(pocApproval));
        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.SITE_SUPERVISOR)).thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.supervisorApprove(100L, new ApprovalRequest(), supervisor);

        assertThat(work.getStatus()).isEqualTo(WorkStatus.SUPERVISOR_APPROVED);
    }
}
