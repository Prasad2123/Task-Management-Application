package com.fieldservice.service;

import com.fieldservice.dto.StartWorkRequest;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.InvalidStateTransitionException;
import com.fieldservice.repository.WorkRepository;
import com.fieldservice.repository.ActivityEventRepository;
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
class WorkServiceTest {

    @Mock
    private WorkRepository workRepository;

    @Mock
    private ActivityEventService activityEventService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private com.fieldservice.repository.ApprovalRepository approvalRepository;

    @Mock
    private WorkReportService workReportService;

    @InjectMocks
    private WorkService workService;

    private UserEntity serviceBoy;
    private UserEntity poc;
    private UserEntity supervisor;
    private UserEntity otherServiceBoy;
    private WorkEntity work;
    private StartWorkRequest validRequest;

    @BeforeEach
    void setUp() {
        serviceBoy = UserEntity.builder()
                .id(1L).name("Rahul").email("service@demo.com")
                .role(UserRole.SERVICE_BOY).active(true).build();

        poc = UserEntity.builder()
                .id(2L).name("Amit").email("poc@demo.com")
                .role(UserRole.POC).active(true).build();

        supervisor = UserEntity.builder()
                .id(3L).name("Suresh").email("supervisor@demo.com")
                .role(UserRole.SITE_SUPERVISOR).active(true).build();

        otherServiceBoy = UserEntity.builder()
                .id(99L).name("Other").email("other@demo.com")
                .role(UserRole.SERVICE_BOY).active(true).build();

        work = WorkEntity.builder()
                .id(10L)
                .title("Test Work")
                .status(WorkStatus.ASSIGNED)
                .serviceBoy(serviceBoy)
                .poc(poc)
                .supervisor(supervisor)
                .latitude(19.1136)
                .longitude(72.8697)
                .allowedRadiusMeters(150.0)
                .build();

        validRequest = StartWorkRequest.builder()
                .latitude(19.1136)
                .longitude(72.8697)
                .accuracyMeters(10.0)
                .build();
    }

    @Test
    void startWork_withValidServiceBoy_setsInProgress() {
        when(workRepository.findById(10L)).thenReturn(Optional.of(work));
        when(workRepository.save(any())).thenReturn(work);

        var response = workService.startWork(10L, validRequest, serviceBoy);

        assertThat(work.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(work.getStartTime()).isNotNull();
        assertThat(response.getLocationVerified()).isTrue();
    }

    @Test
    void startWork_withWrongServiceBoy_throwsAccessDenied() {
        when(workRepository.findById(10L)).thenReturn(Optional.of(work));

        assertThatThrownBy(() -> workService.startWork(10L, validRequest, otherServiceBoy))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void startWork_withNonServiceBoyRole_throwsAccessDenied() {
        assertThatThrownBy(() -> workService.startWork(10L, validRequest, poc))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void startWork_whenAlreadyInProgress_throwsInvalidState() {
        work.setStatus(WorkStatus.IN_PROGRESS);
        when(workRepository.findById(10L)).thenReturn(Optional.of(work));

        assertThatThrownBy(() -> workService.startWork(10L, validRequest, serviceBoy))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void submitForReview_fromInProgress_succeeds() {
        work.setStatus(WorkStatus.IN_PROGRESS);
        when(workRepository.findById(10L)).thenReturn(Optional.of(work));
        when(workRepository.save(any())).thenReturn(work);

        workService.submitForReview(10L, serviceBoy);

        assertThat(work.getStatus()).isEqualTo(WorkStatus.SUBMITTED_FOR_REVIEW);
        assertThat(work.getSubmittedAt()).isNotNull();
    }

    @Test
    void completeWork_withoutSupervisorApproval_throwsInvalidState() {
        work.setStatus(WorkStatus.POC_APPROVED); // Not SUPERVISOR_APPROVED
        when(workRepository.findById(10L)).thenReturn(Optional.of(work));

        assertThatThrownBy(() -> workService.completeWork(10L, serviceBoy))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void completeWork_afterSupervisorApproval_succeeds() {
        work.setStatus(WorkStatus.SUPERVISOR_APPROVED);
        when(workRepository.findById(10L)).thenReturn(Optional.of(work));
        when(workRepository.save(any())).thenReturn(work);

        workService.completeWork(10L, serviceBoy);

        assertThat(work.getStatus()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(work.getCompletedAt()).isNotNull();
    }

    @Test
    void getMyWorks_forServiceBoy_callsServiceBoyRepo() {
        when(workRepository.findByServiceBoyId(1L)).thenReturn(java.util.List.of(work));

        var result = workService.getMyWorks(serviceBoy);

        assertThat(result).hasSize(1);
        verify(workRepository).findByServiceBoyId(1L);
    }

    @Test
    void verifyAccess_serviceBoyAccessingOtherWork_throwsAccessDenied() {
        assertThatThrownBy(() -> workService.verifyAccess(work, otherServiceBoy))
                .isInstanceOf(AccessDeniedException.class);
    }
}
