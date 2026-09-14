package com.fieldservice.service;

import com.fieldservice.dto.UpdatePhotoMetadataRequest;
import com.fieldservice.dto.WorkPhotoResponse;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.ResourceNotFoundException;
import com.fieldservice.mapper.EntityMapper;
import com.fieldservice.repository.WorkPhotoRepository;
import com.fieldservice.storage.PhotoStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final WorkPhotoRepository workPhotoRepository;
    private final WorkService workService;
    private final PhotoStorageService photoStorageService;
    private final ActivityEventService activityEventService;

    /**
     * Upload a real photo for the specified work.
     * Accessible ONLY by the Service Boy assigned to this work.
     */
    @Transactional
    public WorkPhotoResponse uploadPhoto(Long workId,
                                         MultipartFile file,
                                         String title,
                                         PhotoCategory category,
                                         String caption,
                                         UserEntity currentUser) {
        return uploadPhoto(workId, file, title, category, caption, null, currentUser);
    }

    /**
     * Upload a real photo with idempotency client identifier.
     * If a photo with the given clientPhotoId already exists for this work, returns it idempotently.
     */
    @Transactional
    public WorkPhotoResponse uploadPhoto(Long workId,
                                         MultipartFile file,
                                         String title,
                                         PhotoCategory category,
                                         String caption,
                                         String clientPhotoId,
                                         UserEntity currentUser) {
        WorkEntity work = workService.findWorkById(workId);

        // Security check: Only the assigned Service Boy can upload evidence
        if (currentUser.getRole() != UserRole.SERVICE_BOY ||
                work.getServiceBoy() == null ||
                !work.getServiceBoy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the assigned service boy can upload work evidence photos");
        }

        // Idempotency check: Return existing photo if clientPhotoId was already saved
        if (clientPhotoId != null && !clientPhotoId.isBlank()) {
            var existing = workPhotoRepository.findByWorkIdAndClientPhotoId(workId, clientPhotoId.trim());
            if (existing.isPresent()) {
                log.info("Photo with clientPhotoId {} already uploaded for work {}, returning existing", clientPhotoId, workId);
                return EntityMapper.toWorkPhotoResponse(existing.get());
            }
        }

        // Validation: Empty file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Photo file cannot be empty");
        }

        // Validation: File size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 10MB");
        }

        // Validation: Content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file format: " + contentType + ". Allowed: JPEG, PNG, WebP");
        }

        PhotoCategory effectiveCategory = category != null ? category : PhotoCategory.GENERAL;
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo.jpg";
        String effectiveTitle = (title != null && !title.isBlank()) ? title.trim() : effectiveCategory.name() + " Photo";

        // Step 1: Store physical file via storage abstraction
        String storageReference;
        try {
            storageReference = photoStorageService.store(
                    workId,
                    originalFilename,
                    contentType,
                    file.getInputStream(),
                    file.getSize()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded photo content", e);
        }

        // Step 2: Persist metadata in database
        WorkPhotoEntity photoEntity = WorkPhotoEntity.builder()
                .work(work)
                .title(effectiveTitle)
                .category(effectiveCategory)
                .caption(caption != null && !caption.isBlank() ? caption.trim() : null)
                .uploadedBy(currentUser)
                .storageReference(storageReference)
                .uploadStatus(PhotoUploadStatus.UPLOADED)
                .fileName(originalFilename)
                .contentType(contentType)
                .fileSize(file.getSize())
                .clientPhotoId(clientPhotoId != null && !clientPhotoId.isBlank() ? clientPhotoId.trim() : null)
                .build();

        WorkPhotoEntity savedPhoto;
        try {
            savedPhoto = workPhotoRepository.save(photoEntity);
            // Set photoUrl reference
            savedPhoto.setPhotoUrl("/api/works/" + workId + "/photos/" + savedPhoto.getId());
            savedPhoto = workPhotoRepository.save(savedPhoto);
        } catch (Exception e) {
            // Rollback orphan physical file if DB insert fails
            log.error("Failed to save photo metadata, cleaning up stored file {}", storageReference, e);
            photoStorageService.delete(storageReference);
            throw e;
        }

        // Step 3: Record timeline event
        activityEventService.record(
                work,
                ActivityEventType.PHOTO_ADDED,
                "Photo added: " + effectiveTitle + " (" + effectiveCategory.name() + ")",
                currentUser
        );

        return EntityMapper.toWorkPhotoResponse(savedPhoto);
    }

    /**
     * List all photos for a work.
     * Accessible by assigned Service Boy, POC, and Supervisor.
     */
    @Transactional(readOnly = true)
    public List<WorkPhotoResponse> getPhotosForWork(Long workId, UserEntity currentUser) {
        WorkEntity work = workService.findWorkById(workId);
        workService.verifyAccess(work, currentUser);

        return workPhotoRepository.findByWorkIdOrderByCreatedAtAsc(workId)
                .stream()
                .map(EntityMapper::toWorkPhotoResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get photo stream resource and metadata for display / download.
     * Accessible by assigned Service Boy, POC, and Supervisor.
     */
    @Transactional(readOnly = true)
    public PhotoStreamResult getPhotoStream(Long workId, Long photoId, UserEntity currentUser) {
        WorkEntity work = workService.findWorkById(workId);
        workService.verifyAccess(work, currentUser);

        WorkPhotoEntity photo = findPhotoByIdAndWorkId(photoId, workId);
        Resource resource = photoStorageService.loadAsResource(photo.getStorageReference());

        return new PhotoStreamResult(photo, resource);
    }

    /**
     * Update photo metadata (caption, category).
     * Accessible only by the uploader / assigned Service Boy.
     */
    @Transactional
    public WorkPhotoResponse updatePhotoMetadata(Long workId,
                                                 Long photoId,
                                                 UpdatePhotoMetadataRequest request,
                                                 UserEntity currentUser) {
        WorkEntity work = workService.findWorkById(workId);
        verifyModificationPermission(work, currentUser);

        WorkPhotoEntity photo = findPhotoByIdAndWorkId(photoId, workId);

        if (request.getCaption() != null) {
            photo.setCaption(request.getCaption().trim().isEmpty() ? null : request.getCaption().trim());
        }
        if (request.getCategory() != null) {
            photo.setCategory(request.getCategory());
        }

        WorkPhotoEntity updated = workPhotoRepository.save(photo);
        return EntityMapper.toWorkPhotoResponse(updated);
    }

    /**
     * Delete a photo and its physical file.
     * Accessible only by the uploader / assigned Service Boy.
     */
    @Transactional
    public void deletePhoto(Long workId, Long photoId, UserEntity currentUser) {
        WorkEntity work = workService.findWorkById(workId);
        verifyModificationPermission(work, currentUser);

        WorkPhotoEntity photo = findPhotoByIdAndWorkId(photoId, workId);
        String storageRef = photo.getStorageReference();
        String photoTitle = photo.getTitle();

        // Remove from database
        workPhotoRepository.delete(photo);

        // Remove physical file
        photoStorageService.delete(storageRef);

        // Record activity event
        activityEventService.record(
                work,
                ActivityEventType.PHOTO_DELETED,
                "Photo removed: " + photoTitle,
                currentUser
        );
    }

    private WorkPhotoEntity findPhotoByIdAndWorkId(Long photoId, Long workId) {
        WorkPhotoEntity photo = workPhotoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found with id " + photoId));

        if (!photo.getWork().getId().equals(workId)) {
            throw new ResourceNotFoundException("Photo " + photoId + " does not belong to work " + workId);
        }
        return photo;
    }

    private void verifyModificationPermission(WorkEntity work, UserEntity currentUser) {
        if (currentUser.getRole() != UserRole.SERVICE_BOY ||
                work.getServiceBoy() == null ||
                !work.getServiceBoy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the assigned service boy can modify or delete evidence photos");
        }
    }

    public record PhotoStreamResult(WorkPhotoEntity photo, Resource resource) {}
}
