package com.fieldservice.service;

import com.fieldservice.dto.UpdatePhotoMetadataRequest;
import com.fieldservice.dto.WorkPhotoResponse;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.ResourceNotFoundException;
import com.fieldservice.repository.WorkPhotoRepository;
import com.fieldservice.storage.PhotoStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock
    private WorkPhotoRepository workPhotoRepository;

    @Mock
    private WorkService workService;

    @Mock
    private PhotoStorageService photoStorageService;

    @Mock
    private ActivityEventService activityEventService;

    @InjectMocks
    private PhotoService photoService;

    private UserEntity serviceBoy;
    private UserEntity poc;
    private UserEntity supervisor;
    private UserEntity otherServiceBoy;
    private WorkEntity work;
    private WorkPhotoEntity samplePhoto;

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
                .id(4L).name("Karan").email("karan@demo.com")
                .role(UserRole.SERVICE_BOY).active(true).build();

        work = WorkEntity.builder()
                .id(100L)
                .title("Monthly Pest Control")
                .serviceBoy(serviceBoy)
                .poc(poc)
                .supervisor(supervisor)
                .status(WorkStatus.IN_PROGRESS)
                .build();

        samplePhoto = WorkPhotoEntity.builder()
                .id(10L)
                .work(work)
                .title("Initial Inspection")
                .category(PhotoCategory.SITE_INSPECTION)
                .caption("Main entry inspection")
                .uploadedBy(serviceBoy)
                .storageReference("100/sample.jpg")
                .fileName("sample.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .photoUrl("/api/works/100/photos/10")
                .uploadStatus(PhotoUploadStatus.UPLOADED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void testUploadValidJpeg_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "inspection.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes()
        );

        when(workService.findWorkById(100L)).thenReturn(work);
        when(photoStorageService.store(eq(100L), eq("inspection.jpg"), eq("image/jpeg"), any(InputStream.class), anyLong()))
                .thenReturn("100/uuid-inspection.jpg");
        when(workPhotoRepository.save(any(WorkPhotoEntity.class))).thenAnswer(inv -> {
            WorkPhotoEntity p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        WorkPhotoResponse response = photoService.uploadPhoto(
                100L, file, "Inspection Photo", PhotoCategory.SITE_INSPECTION, "Front gate", serviceBoy
        );

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getTitle()).isEqualTo("Inspection Photo");
        assertThat(response.getCategory()).isEqualTo("SITE_INSPECTION");
        assertThat(response.getCaption()).isEqualTo("Front gate");
        assertThat(response.getUploadStatus()).isEqualTo("UPLOADED");

        verify(photoStorageService).store(eq(100L), eq("inspection.jpg"), eq("image/jpeg"), any(), anyLong());
        verify(workPhotoRepository, atLeastOnce()).save(any(WorkPhotoEntity.class));
        verify(activityEventService).record(eq(work), eq(ActivityEventType.PHOTO_ADDED), anyString(), eq(serviceBoy));
    }

    @Test
    void testUploadValidPng_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "treatment.png", "image/png", "fake-png-bytes".getBytes()
        );

        when(workService.findWorkById(100L)).thenReturn(work);
        when(photoStorageService.store(eq(100L), eq("treatment.png"), eq("image/png"), any(InputStream.class), anyLong()))
                .thenReturn("100/uuid-treatment.png");
        when(workPhotoRepository.save(any(WorkPhotoEntity.class))).thenAnswer(inv -> {
            WorkPhotoEntity p = inv.getArgument(0);
            p.setId(11L);
            return p;
        });

        WorkPhotoResponse response = photoService.uploadPhoto(
                100L, file, "Treatment Area", PhotoCategory.TREATMENT_APPLICATION, null, serviceBoy
        );

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getContentType()).isEqualTo("image/png");
    }

    @Test
    void testUploadInvalidMimeType_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "fake-pdf".getBytes()
        );
        when(workService.findWorkById(100L)).thenReturn(work);

        assertThatThrownBy(() -> photoService.uploadPhoto(100L, file, "PDF Doc", PhotoCategory.GENERAL, null, serviceBoy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file format");

        verifyNoInteractions(photoStorageService);
    }

    @Test
    void testUploadOversizedFile_rejected() {
        byte[] largeBytes = new byte[100];
        MockMultipartFile file = new MockMultipartFile("file", "large.jpg", "image/jpeg", largeBytes) {
            @Override
            public long getSize() {
                return PhotoService.MAX_FILE_SIZE + 1024L; // > 10MB
            }
        };
        when(workService.findWorkById(100L)).thenReturn(work);

        assertThatThrownBy(() -> photoService.uploadPhoto(100L, file, "Big", PhotoCategory.GENERAL, null, serviceBoy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum limit");

        verifyNoInteractions(photoStorageService);
    }

    @Test
    void testUploadEmptyFile_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]
        );
        when(workService.findWorkById(100L)).thenReturn(work);

        assertThatThrownBy(() -> photoService.uploadPhoto(100L, file, "Empty", PhotoCategory.GENERAL, null, serviceBoy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");

        verifyNoInteractions(photoStorageService);
    }

    @Test
    void testUploadToNonexistentWork_throwsNotFound() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes()
        );
        when(workService.findWorkById(999L)).thenThrow(new ResourceNotFoundException("Work not found with id 999"));

        assertThatThrownBy(() -> photoService.uploadPhoto(999L, file, "Photo", PhotoCategory.GENERAL, null, serviceBoy))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testUploadUnauthorizedUser_pocCannotUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes()
        );
        when(workService.findWorkById(100L)).thenReturn(work);

        assertThatThrownBy(() -> photoService.uploadPhoto(100L, file, "Photo", PhotoCategory.GENERAL, null, poc))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the assigned service boy");

        verifyNoInteractions(photoStorageService);
    }

    @Test
    void testUploadUnauthorizedUser_otherServiceBoyCannotUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes()
        );
        when(workService.findWorkById(100L)).thenReturn(work);

        assertThatThrownBy(() -> photoService.uploadPhoto(100L, file, "Photo", PhotoCategory.GENERAL, null, otherServiceBoy))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the assigned service boy");
    }

    @Test
    void testListPhotos_success() {
        when(workService.findWorkById(100L)).thenReturn(work);
        when(workPhotoRepository.findByWorkIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(samplePhoto));

        List<WorkPhotoResponse> photos = photoService.getPhotosForWork(100L, poc);

        assertThat(photos).hasSize(1);
        assertThat(photos.get(0).getId()).isEqualTo(10L);
        verify(workService).verifyAccess(work, poc);
    }

    @Test
    void testRetrievePhotoStream_success() {
        Resource mockResource = new ByteArrayResource("fake-image".getBytes());
        when(workService.findWorkById(100L)).thenReturn(work);
        when(workPhotoRepository.findById(10L)).thenReturn(Optional.of(samplePhoto));
        when(photoStorageService.loadAsResource("100/sample.jpg")).thenReturn(mockResource);

        PhotoService.PhotoStreamResult result = photoService.getPhotoStream(100L, 10L, supervisor);

        assertThat(result).isNotNull();
        assertThat(result.photo().getId()).isEqualTo(10L);
        assertThat(result.resource()).isEqualTo(mockResource);
        verify(workService).verifyAccess(work, supervisor);
    }

    @Test
    void testRetrievePhoto_photoNotBelongingToWork_rejected() {
        WorkEntity otherWork = WorkEntity.builder().id(200L).build();
        WorkPhotoEntity photoOnOtherWork = WorkPhotoEntity.builder()
                .id(50L).work(otherWork).storageReference("200/photo.jpg").build();

        when(workService.findWorkById(100L)).thenReturn(work);
        when(workPhotoRepository.findById(50L)).thenReturn(Optional.of(photoOnOtherWork));

        assertThatThrownBy(() -> photoService.getPhotoStream(100L, 50L, serviceBoy))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("does not belong to work");
    }

    @Test
    void testDeletePhoto_success() {
        when(workService.findWorkById(100L)).thenReturn(work);
        when(workPhotoRepository.findById(10L)).thenReturn(Optional.of(samplePhoto));

        photoService.deletePhoto(100L, 10L, serviceBoy);

        verify(workPhotoRepository).delete(samplePhoto);
        verify(photoStorageService).delete("100/sample.jpg");
        verify(activityEventService).record(eq(work), eq(ActivityEventType.PHOTO_DELETED), anyString(), eq(serviceBoy));
    }

    @Test
    void testDeletePhoto_unauthorizedUser_pocCannotDelete() {
        when(workService.findWorkById(100L)).thenReturn(work);

        assertThatThrownBy(() -> photoService.deletePhoto(100L, 10L, poc))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the assigned service boy");

        verify(workPhotoRepository, never()).delete(any());
        verify(photoStorageService, never()).delete(any());
    }

    @Test
    void testUpdateCaptionAndCategory_success() {
        when(workService.findWorkById(100L)).thenReturn(work);
        when(workPhotoRepository.findById(10L)).thenReturn(Optional.of(samplePhoto));
        when(workPhotoRepository.save(any(WorkPhotoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePhotoMetadataRequest request = UpdatePhotoMetadataRequest.builder()
                .caption("Updated caption details")
                .category(PhotoCategory.EQUIPMENT_CHECK)
                .build();

        WorkPhotoResponse updated = photoService.updatePhotoMetadata(100L, 10L, request, serviceBoy);

        assertThat(updated.getCaption()).isEqualTo("Updated caption details");
        assertThat(updated.getCategory()).isEqualTo("EQUIPMENT_CHECK");
    }

    @Test
    void testUpdatePhoto_unauthorizedUser_supervisorCannotUpdate() {
        when(workService.findWorkById(100L)).thenReturn(work);

        UpdatePhotoMetadataRequest request = UpdatePhotoMetadataRequest.builder()
                .caption("Hacked")
                .build();

        assertThatThrownBy(() -> photoService.updatePhotoMetadata(100L, 10L, request, supervisor))
                .isInstanceOf(AccessDeniedException.class);

        verify(workPhotoRepository, never()).save(any());
    }
}
