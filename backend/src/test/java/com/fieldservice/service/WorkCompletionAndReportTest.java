package com.fieldservice.service;

import com.fieldservice.dto.WorkReportDto;
import com.fieldservice.dto.WorkResponse;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.BadRequestException;
import com.fieldservice.exception.InvalidStateTransitionException;
import com.fieldservice.repository.*;
import com.fieldservice.storage.PhotoStorageService;
import com.fieldservice.storage.ReportStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkCompletionAndReportTest {

    @Mock
    private WorkRepository workRepository;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ApprovalRepository approvalRepository;

    @Mock
    private WorkReportRepository workReportRepository;
    @Mock
    private ChecklistItemRepository checklistItemRepository;
    @Mock
    private AdditionalWorkRepository additionalWorkRepository;
    @Mock
    private WorkPhotoRepository workPhotoRepository;
    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private PhotoStorageService photoStorageService;
    @Mock
    private ReportStorageService reportStorageService;

    private WorkService workService;
    private WorkReportService workReportService;

    private UserEntity serviceBoy;
    private UserEntity poc;
    private UserEntity supervisor;
    private UserEntity unauthorizedUser;
    private WorkEntity work;

    @BeforeEach
    void setUp() {
        serviceBoy = UserEntity.builder()
                .id(101L).name("Rahul Sharma").email("rahul@fieldservice.com")
                .role(UserRole.SERVICE_BOY).active(true).build();

        poc = UserEntity.builder()
                .id(102L).name("Amit Verma").email("amit@client.com")
                .role(UserRole.POC).active(true).build();

        supervisor = UserEntity.builder()
                .id(103L).name("Vikram Singh").email("vikram@fieldservice.com")
                .role(UserRole.SITE_SUPERVISOR).active(true).build();

        unauthorizedUser = UserEntity.builder()
                .id(999L).name("Intruder").email("intruder@evil.com")
                .role(UserRole.SERVICE_BOY).active(true).build();

        work = WorkEntity.builder()
                .id(501L)
                .title("AC Compressor Repair")
                .description("Repair main VRF compressor unit on rooftop")
                .status(WorkStatus.SUPERVISOR_APPROVED)
                .companyName("Block B, Technopark")
                .address("123 Tech Park Road")
                .latitude(12.9716)
                .longitude(77.5946)
                .serviceBoy(serviceBoy)
                .poc(poc)
                .supervisor(supervisor)
                .startTime(Instant.now().minus(2, ChronoUnit.HOURS).minus(15, ChronoUnit.MINUTES))
                .build();

        workReportService = new WorkReportService(
                workReportRepository,
                workRepository,
                checklistItemRepository,
                additionalWorkRepository,
                workPhotoRepository,
                approvalRepository,
                activityEventRepository,
                photoStorageService,
                reportStorageService
        );

        workService = new WorkService(
                workRepository,
                activityEventService,
                notificationService,
                approvalRepository,
                workReportService
        );
    }

    @Test
    @DisplayName("Complete work sets server-authoritative completedAt and transitions status")
    void completeWork_validState_setsAuthoritativeCompletion() {
        when(workRepository.findById(501L)).thenReturn(Optional.of(work));
        when(workRepository.save(any(WorkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkResponse response = workService.completeWork(501L, serviceBoy);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(WorkStatus.COMPLETED.name());
        assertThat(work.getStatus()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(work.getCompletedAt()).isNotNull();

        // Check activity logged
        verify(activityEventService).record(eq(work), eq(ActivityEventType.WORK_COMPLETED), anyString(), eq(serviceBoy));
    }

    @Test
    @DisplayName("Complete work sends three-way notification to Service Boy, POC, and Supervisor")
    void completeWork_sendsThreeWayNotifications() {
        when(workRepository.findById(501L)).thenReturn(Optional.of(work));
        when(workRepository.save(any(WorkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        workService.completeWork(501L, serviceBoy);

        // Verify notification sent to Service Boy
        verify(notificationService).createNotification(
                eq(serviceBoy),
                eq(work),
                eq(NotificationType.WORK_COMPLETED),
                argThat(t -> t.contains("Completed")),
                anyString()
        );

        // Verify notification sent to POC
        verify(notificationService).createNotification(
                eq(poc),
                eq(work),
                eq(NotificationType.WORK_COMPLETED),
                argThat(t -> t.contains("Completed")),
                anyString()
        );

        // Verify notification sent to Supervisor
        verify(notificationService).createNotification(
                eq(supervisor),
                eq(work),
                eq(NotificationType.WORK_COMPLETED),
                argThat(t -> t.contains("Completed")),
                anyString()
        );
    }

    @Test
    @DisplayName("Complete work is idempotent - returning immediately if already COMPLETED")
    void completeWork_isIdempotent() {
        work.setStatus(WorkStatus.COMPLETED);
        work.setCompletedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        when(workRepository.findById(501L)).thenReturn(Optional.of(work));

        WorkResponse response = workService.completeWork(501L, serviceBoy);

        assertThat(response.getStatus()).isEqualTo(WorkStatus.COMPLETED.name());
        // Repository save should not be called again
        verify(workRepository, never()).save(any());
        // Notification should not be sent again
        verify(notificationService, never()).createNotification(any(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Complete work fails if work is not in SUPERVISOR_APPROVED state")
    void completeWork_withoutSupervisorApproval_throwsInvalidState() {
        work.setStatus(WorkStatus.POC_APPROVED);
        when(workRepository.findById(501L)).thenReturn(Optional.of(work));

        assertThatThrownBy(() -> workService.completeWork(501L, serviceBoy))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Supervisor approval");
    }

    @Test
    @DisplayName("WorkReportService generates PDF, saves entity and returns DTO")
    void generateReport_successfulGeneration() {
        work.setStatus(WorkStatus.COMPLETED);
        work.setCompletedAt(Instant.now());

        when(workReportRepository.findByWorkId(501L)).thenReturn(Optional.empty());
        when(checklistItemRepository.findByWorkIdOrderByDisplayOrderAscCreatedAtAsc(501L)).thenReturn(List.of(
                ChecklistItemEntity.builder().id(1L).title("Check refrigerant pressure").completed(true).displayOrder(1).build(),
                ChecklistItemEntity.builder().id(2L).title("Inspect condenser coils").completed(true).displayOrder(2).build()
        ));
        when(additionalWorkRepository.findByWorkIdOrderByCreatedAtAsc(501L)).thenReturn(List.of(
                AdditionalWorkEntity.builder().id(1L).description("Replaced expansion valve filter").build()
        ));
        when(workPhotoRepository.findByWorkIdOrderByCreatedAtAsc(501L)).thenReturn(List.of());
        when(approvalRepository.findByWorkIdOrderByCreatedAtAsc(501L)).thenReturn(List.of(
                ApprovalEntity.builder().id(1L).approverRole(UserRole.POC).status(ApprovalStatus.APPROVED).approver(poc).build(),
                ApprovalEntity.builder().id(2L).approverRole(UserRole.SITE_SUPERVISOR).status(ApprovalStatus.APPROVED).approver(supervisor).build()
        ));
        when(activityEventRepository.findByWorkIdOrderByEventTimestampAsc(501L)).thenReturn(List.of(
                ActivityEventEntity.builder().id(1L).eventType(ActivityEventType.WORK_STARTED).description("Work started").eventTimestamp(work.getStartTime()).build(),
                ActivityEventEntity.builder().id(2L).eventType(ActivityEventType.WORK_COMPLETED).description("Work finished").eventTimestamp(work.getCompletedAt()).build()
        ));

        when(reportStorageService.store(eq(501L), anyString(), any(InputStream.class), anyLong()))
                .thenReturn("reports/501/WorkReport_501.pdf");

        when(workReportRepository.save(any(WorkReportEntity.class))).thenAnswer(invocation -> {
            WorkReportEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        WorkReportDto reportDto = workReportService.generateReport(work);

        assertThat(reportDto).isNotNull();
        assertThat(reportDto.getWorkId()).isEqualTo(501L);
        assertThat(reportDto.getReportNumber()).startsWith("REP-W501-");
        assertThat(reportDto.getContentType()).isEqualTo("application/pdf");
        assertThat(reportDto.getDownloadUrl()).contains("/api/works/501/report/download");
        assertThat(reportDto.getDuration()).contains("2h");

        // Verify save was called
        verify(workReportRepository).save(any(WorkReportEntity.class));
    }

    @Test
    @DisplayName("WorkReportService reuses existing report if already generated (Idempotency)")
    void generateReport_reusesExisting() {
        WorkReportEntity existing = WorkReportEntity.builder()
                .id(15L)
                .work(work)
                .reportNumber("REP-W501-EXISTING")
                .storageReference("reports/501/existing.pdf")
                .fileName("existing.pdf")
                .contentType("application/pdf")
                .fileSize(12345L)
                .generatedAt(Instant.now())
                .createdBy(serviceBoy)
                .version(1)
                .build();

        when(workReportRepository.findByWorkId(501L)).thenReturn(Optional.of(existing));

        WorkReportDto reportDto = workReportService.generateReport(work);

        assertThat(reportDto.getReportNumber()).isEqualTo("REP-W501-EXISTING");
        verify(workReportRepository, never()).save(any());
        verify(reportStorageService, never()).store(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("getReportMetadata throws ForbiddenException if user is not a stakeholder")
    void getReportMetadata_unauthorizedUser_throwsForbidden() {
        when(workRepository.findById(501L)).thenReturn(Optional.of(work));

        assertThatThrownBy(() -> workReportService.getReportMetadata(501L, unauthorizedUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("authorized");
    }

    @Test
    @DisplayName("getReportMetadata throws BadRequestException if work is not COMPLETED")
    void getReportMetadata_workNotCompleted_throwsBadRequest() {
        work.setStatus(WorkStatus.SUPERVISOR_APPROVED); // Not COMPLETED yet
        when(workRepository.findById(501L)).thenReturn(Optional.of(work));

        assertThatThrownBy(() -> workReportService.getReportMetadata(501L, serviceBoy))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("getReportPdfResource returns storage resource for authorized stakeholder")
    void getReportPdfResource_authorizedStakeholder_returnsResource() {
        work.setStatus(WorkStatus.COMPLETED);
        when(workRepository.findById(501L)).thenReturn(Optional.of(work));

        WorkReportEntity report = WorkReportEntity.builder()
                .id(1L)
                .work(work)
                .storageReference("reports/501/report.pdf")
                .build();
        when(workReportRepository.findByWorkId(501L)).thenReturn(Optional.of(report));

        Resource mockResource = new ByteArrayResource("PDF-DATA".getBytes());
        when(reportStorageService.loadAsResource("reports/501/report.pdf")).thenReturn(mockResource);

        Resource result = workReportService.getReportPdfResource(501L, poc);

        assertThat(result).isNotNull();
        assertThat(result.exists()).isTrue();
    }
}
