package com.fieldservice.controller;

import com.fieldservice.dto.UpdatePhotoMetadataRequest;
import com.fieldservice.dto.WorkPhotoResponse;
import com.fieldservice.entity.PhotoCategory;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.security.SecurityUtils;
import com.fieldservice.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/works/{workId}/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    /**
     * POST /api/works/{workId}/photos
     * Upload real photo multipart file with metadata.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkPhotoResponse> uploadPhoto(
            @PathVariable Long workId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) PhotoCategory category,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "clientPhotoId", required = false) String clientPhotoId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        WorkPhotoResponse response = (clientPhotoId != null && !clientPhotoId.isBlank())
                ? photoService.uploadPhoto(workId, file, title, category, caption, clientPhotoId.trim(), currentUser)
                : photoService.uploadPhoto(workId, file, title, category, caption, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/works/{workId}/photos
     * List all photos for the work.
     */
    @GetMapping
    public ResponseEntity<List<WorkPhotoResponse>> getPhotos(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        List<WorkPhotoResponse> photos = photoService.getPhotosForWork(workId, currentUser);
        return ResponseEntity.ok(photos);
    }

    /**
     * GET /api/works/{workId}/photos/{photoId}
     * Retrieve/stream the actual image binary.
     */
    @GetMapping("/{photoId}")
    public ResponseEntity<Resource> getPhotoStream(
            @PathVariable Long workId,
            @PathVariable Long photoId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        PhotoService.PhotoStreamResult result = photoService.getPhotoStream(workId, photoId, currentUser);

        String contentType = result.photo().getContentType() != null ? result.photo().getContentType() : MediaType.IMAGE_JPEG_VALUE;
        String fileName = result.photo().getFileName() != null ? result.photo().getFileName() : "photo.jpg";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("inline", fileName);
        headers.setCacheControl("private, max-age=86400");
        if (result.photo().getFileSize() != null && result.photo().getFileSize() > 0) {
            headers.setContentLength(result.photo().getFileSize());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(result.resource());
    }

    /**
     * PATCH /api/works/{workId}/photos/{photoId}
     * Update photo caption and/or category.
     */
    @PatchMapping("/{photoId}")
    public ResponseEntity<WorkPhotoResponse> updatePhotoMetadata(
            @PathVariable Long workId,
            @PathVariable Long photoId,
            @RequestBody UpdatePhotoMetadataRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        WorkPhotoResponse response = photoService.updatePhotoMetadata(workId, photoId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/works/{workId}/photos/{photoId}
     * Delete photo metadata and physical file.
     */
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable Long workId,
            @PathVariable Long photoId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        photoService.deletePhoto(workId, photoId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
