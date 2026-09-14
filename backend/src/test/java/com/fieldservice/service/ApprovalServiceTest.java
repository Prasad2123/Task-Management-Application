package com.fieldservice.service;

import com.fieldservice.dto.ApprovalRequest;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.ConflictException;
import com.fieldservice.exception.InvalidStateTransitionException;
import com.fieldservice.repository.ApprovalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRepository approvalRepository;

    @Mock
    private WorkService workService;

    @Mock
    private ActivityEventService activityEventService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ApprovalService approvalService;

    private UserEntity poc;
    private UserEntity supervisor;
    private UserEntity serviceBoy;
    private WorkEntity work;
    private ApprovalRequest approvalRequest;

    @BeforeEach
    void setUp() {
        serviceBoy = UserEntity.builder().id(1L).name("Rahul").role(UserRole.SERVICE_BOY).active(true).build();
        poc = UserEntity.builder().id(2L).name("Amit").role(UserRole.POC).active(true).build();
        supervisor = UserEntity.builder().id(3L).name("Suresh").role(UserRole.SITE_SUPERVISOR).active(true).build();

        work = WorkEntity.builder()
                .id(10L).title("Test Work")
                .status(WorkStatus.SUBMITTED_FOR_REVIEW)
                .serviceBoy(serviceBoy).poc(poc).supervisor(supervisor)
                .build();

        approvalRequest = new ApprovalRequest();
    }

    @Test
    void pocApprove_withValidPoc_approvesAndUpdatesStatus() {
        when(workService.findWorkById(10L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(10L, UserRole.POC))
                .thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workService.saveWork(any())).thenReturn(work);

        approvalService.pocApprove(10L, approvalRequest, poc);

        assertThat(work.getStatus()).isEqualTo(WorkStatus.POC_APPROVED);
    }

    @Test
    void pocApprove_withDuplicateApproval_throwsConflict() {
        ApprovalEntity existing = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.POC).build();

        when(workService.findWorkById(10L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(10L, UserRole.POC))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> approvalService.pocApprove(10L, approvalRequest, poc))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void pocApprove_withWrongRole_throwsAccessDenied() {
        assertThatThrownBy(() -> approvalService.pocApprove(10L, approvalRequest, serviceBoy))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void supervisorApprove_beforePocApproval_throwsInvalidState() {
        work.setStatus(WorkStatus.SUBMITTED_FOR_REVIEW); // Not POC_APPROVED
        when(workService.findWorkById(10L)).thenReturn(work);

        assertThatThrownBy(() -> approvalService.supervisorApprove(10L, approvalRequest, supervisor))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void supervisorApprove_afterPocApproval_succeeds() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.POC).build();
        when(approvalRepository.findByWorkIdAndApproverRole(10L, UserRole.POC))
                .thenReturn(Optional.of(pocApproval));

        when(workService.findWorkById(10L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(10L, UserRole.SITE_SUPERVISOR))
                .thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workService.saveWork(any())).thenReturn(work);

        approvalService.supervisorApprove(10L, approvalRequest, supervisor);

        assertThat(work.getStatus()).isEqualTo(WorkStatus.SUPERVISOR_APPROVED);
    }

    @Test
    void pocReject_setsStatusRejected() {
        ApprovalRequest rejectRequest = new ApprovalRequest();
        rejectRequest.setReason("Incomplete work");

        when(workService.findWorkById(10L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(10L, UserRole.POC))
                .thenReturn(Optional.empty());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workService.saveWork(any())).thenReturn(work);

        approvalService.pocReject(10L, rejectRequest, poc);

        assertThat(work.getStatus()).isEqualTo(WorkStatus.REJECTED);
    }

    @Test
    void supervisorApprove_withDuplicateApproval_throwsConflict() {
        work.setStatus(WorkStatus.POC_APPROVED);
        ApprovalEntity pocApproval = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.POC).build();
        when(approvalRepository.findByWorkIdAndApproverRole(10L, UserRole.POC))
                .thenReturn(Optional.of(pocApproval));

        ApprovalEntity existing = ApprovalEntity.builder()
                .status(ApprovalStatus.APPROVED)
                .approverRole(UserRole.SITE_SUPERVISOR).build();

        when(workService.findWorkById(10L)).thenReturn(work);
        when(approvalRepository.findByWorkIdAndApproverRole(10L, UserRole.SITE_SUPERVISOR))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> approvalService.supervisorApprove(10L, approvalRequest, supervisor))
                .isInstanceOf(ConflictException.class);
    }
}
