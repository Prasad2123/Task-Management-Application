package com.fieldservice.service;

import com.fieldservice.dto.*;
import com.fieldservice.entity.*;
import com.fieldservice.repository.*;
import com.fieldservice.storage.PhotoStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NetworkSyncAndIdempotencyTest {

    @Mock
    private WorkRepository workRepository;
    @Mock
    private WorkPhotoRepository workPhotoRepository;
    @Mock
    private AdditionalWorkRepository additionalWorkRepository;
    @Mock
    private ApprovalRepository approvalRepository;
    @Mock
    private WorkService workService;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PhotoStorageService photoStorageService;

    private WorkService directWorkService;
    private PhotoService photoService;
    private AdditionalWorkService additionalWorkService;
    private ApprovalService approvalService;

    private UserEntity serviceBoy;
    private UserEntity poc;
    private UserEntity supervisor;
    private WorkEntity work;

    @BeforeEach
    void setUp() {
        serviceBoy = UserEntity.builder().id(1L).name("Raju").role(UserRole.SERVICE_BOY).active(true).build();
        poc = UserEntity.builder().id(2L).name("Priya").role(UserRole.POC).active(true).build();
        supervisor = UserEntity.builder().id(3L).name("Vikram").role(UserRole.SITE_SUPERVISOR).active(true).build();

        work = WorkEntity.builder()
                .id(100L)
                .title("Telecom Tower Inspection")
                .status(WorkStatus.ASSIGNED)
                .serviceBoy(serviceBoy)
                .poc(poc)
                .supervisor(supervisor)
                .latitude(12.9716)
                .longitude(77.5946)
                .build();

        directWorkService = new WorkService(
                workRepository, activityEventService, notificationService, approvalRepository);

        photoService = new PhotoService(
                workPhotoRepository, workService, photoStorageService, activityEventService);

        additionalWorkService = new AdditionalWorkService(
                additionalWorkRepository, workService, activityEventService);

        approvalService = new ApprovalService(
                approvalRepository, workService, activityEventService, notificationService);
    }

    @Test
    @DisplayName("WorkService.startWork throws InvalidStateTransitionException if work is already IN_PROGRESS")
    void testStartWorkAlreadyInProgress() {
        work.setStatus(WorkStatus.IN_PROGRESS);
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));

        StartWorkRequest request = new StartWorkRequest();
        request.setLatitude(12.9716);
        request.setLongitude(77.5946);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> directWorkService.startWork(100L, request, serviceBoy))
                .isInstanceOf(com.fieldservice.exception.InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("WorkService.submitForReview throws InvalidStateTransitionException if work is not IN_PROGRESS")
    void testSubmitForReviewNotInProgress() {
        work.setStatus(WorkStatus.SUBMITTED_FOR_REVIEW);
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> directWorkService.submitForReview(100L, serviceBoy))
                .isInstanceOf(com.fieldservice.exception.InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("PhotoService.uploadPhoto deduplicates using clientPhotoId")
    void testPhotoUploadIdempotent() {
        when(workService.findWorkById(100L)).thenReturn(work);

        WorkPhotoEntity existingPhoto = WorkPhotoEntity.builder()
                .id(50L)
                .work(work)
                .title("Initial Inspection")
                .category(PhotoCategory.SITE_INSPECTION)
                .caption("Front view")
                .photoUrl("/api/works/100/photos/50")
                .clientPhotoId("client-uuid-12345")
                .build();

        when(workPhotoRepository.findByWorkIdAndClientPhotoId(100L, "client-uuid-12345"))
                .thenReturn(Optional.of(existingPhoto));

        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});

        WorkPhotoResponse response = photoService.uploadPhoto(
                100L, file, "Initial Inspection", PhotoCategory.SITE_INSPECTION, "Front view", "client-uuid-12345", serviceBoy);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getClientPhotoId()).isEqualTo("client-uuid-12345");
        verify(photoStorageService, never()).store(any(), any(), any(), any(), anyLong());
        verify(workPhotoRepository, never()).save(any());
    }

    @Test
    @DisplayName("AdditionalWorkService deduplicates using clientItemId")
    void testAdditionalWorkIdempotent() {
        when(workService.findWorkById(100L)).thenReturn(work);

        AdditionalWorkEntity existingItem = AdditionalWorkEntity.builder()
                .id(77L)
                .work(work)
                .description("Replaced blown 10A fuse")
                .clientItemId("item-uuid-999")
                .createdBy(serviceBoy)
                .build();

        when(additionalWorkRepository.findByWorkIdAndClientItemId(100L, "item-uuid-999"))
                .thenReturn(Optional.of(existingItem));

        AdditionalWorkRequest request = new AdditionalWorkRequest();
        request.setDescription("Replaced blown 10A fuse");
        request.setClientItemId("item-uuid-999");

        AdditionalWorkResponse response = additionalWorkService.create(100L, request, serviceBoy);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(77L);
        assertThat(response.getClientItemId()).isEqualTo("item-uuid-999");
        verify(additionalWorkRepository, never()).save(any());
    }

    @Test
    @DisplayName("ApprovalService.pocApprove throws ConflictException if already APPROVED")
    void testPocApproveAlreadyApproved() {
        work.setStatus(WorkStatus.SUBMITTED_FOR_REVIEW);
        when(workService.findWorkById(100L)).thenReturn(work);

        ApprovalEntity existingApproval = ApprovalEntity.builder()
                .id(201L)
                .work(work)
                .approver(poc)
                .approverRole(UserRole.POC)
                .status(ApprovalStatus.APPROVED)
                .decidedAt(Instant.now())
                .build();

        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC))
                .thenReturn(Optional.of(existingApproval));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> approvalService.pocApprove(100L, new ApprovalRequest(), poc))
                .isInstanceOf(com.fieldservice.exception.ConflictException.class);
    }

    @Test
    @DisplayName("ApprovalService.supervisorApprove throws ConflictException if already APPROVED")
    void testSupervisorApproveAlreadyApproved() {
        work.setStatus(WorkStatus.POC_APPROVED);
        when(workService.findWorkById(100L)).thenReturn(work);

        ApprovalEntity pocApproval = ApprovalEntity.builder()
                .id(201L)
                .work(work)
                .approver(poc)
                .approverRole(UserRole.POC)
                .status(ApprovalStatus.APPROVED)
                .decidedAt(Instant.now())
                .build();

        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.POC))
                .thenReturn(Optional.of(pocApproval));

        ApprovalEntity existingApproval = ApprovalEntity.builder()
                .id(202L)
                .work(work)
                .approver(supervisor)
                .approverRole(UserRole.SITE_SUPERVISOR)
                .status(ApprovalStatus.APPROVED)
                .decidedAt(Instant.now())
                .build();

        when(approvalRepository.findByWorkIdAndApproverRole(100L, UserRole.SITE_SUPERVISOR))
                .thenReturn(Optional.of(existingApproval));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> approvalService.supervisorApprove(100L, new ApprovalRequest(), supervisor))
                .isInstanceOf(com.fieldservice.exception.ConflictException.class);
    }
}
