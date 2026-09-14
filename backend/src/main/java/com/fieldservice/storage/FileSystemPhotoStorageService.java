package com.fieldservice.storage;

import com.fieldservice.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Filesystem-backed implementation of PhotoStorageService.
 * Suitable for local development and standalone single-instance deployments.
 * Implements strict path normalization to prevent path traversal attacks,
 * and enforces server-generated UUID filenames.
 */
@Slf4j
@Service
public class FileSystemPhotoStorageService implements PhotoStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Path rootLocation;

    public FileSystemPhotoStorageService(@Value("${app.storage.photos-dir:storage/photos}") String storageDir) {
        this.rootLocation = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("Initialized photo storage directory at: {}", rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize photo storage directory at " + rootLocation, e);
        }
    }

    @Override
    public String store(Long workId, String originalFilename, String contentType, InputStream inputStream, long size) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported media type: " + contentType + ". Allowed types: image/jpeg, image/png, image/webp");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        String extension = determineExtension(contentType, originalFilename);
        String safeFileName = UUID.randomUUID().toString() + extension;

        Path workDirectory = rootLocation.resolve(String.valueOf(workId)).normalize();
        Path destinationFile = workDirectory.resolve(safeFileName).normalize();

        // Security check: Path traversal prevention
        if (!destinationFile.startsWith(rootLocation)) {
            throw new SecurityException("Cannot store file outside current storage directory");
        }

        try {
            Files.createDirectories(workDirectory);
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored photo for work {} at {}", workId, destinationFile);
            // Return normalized relative storage key: "workId/filename"
            return workId + "/" + safeFileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store photo file", e);
        }
    }

    @Override
    public Resource loadAsResource(String storageReference) {
        if (storageReference == null || storageReference.isBlank()) {
            throw new ResourceNotFoundException("Storage reference is empty");
        }

        Path filePath = rootLocation.resolve(storageReference).normalize();

        // Security check: Path traversal prevention
        if (!filePath.startsWith(rootLocation)) {
            throw new SecurityException("Access to path outside storage directory is denied");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Photo file not found at " + storageReference);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Photo file URL invalid for " + storageReference, e);
        }
    }

    @Override
    public void delete(String storageReference) {
        if (storageReference == null || storageReference.isBlank()) {
            return;
        }

        Path filePath = rootLocation.resolve(storageReference).normalize();

        // Security check: Path traversal prevention
        if (!filePath.startsWith(rootLocation)) {
            throw new SecurityException("Access to path outside storage directory is denied");
        }

        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.debug("Deleted photo file at {}", filePath);
            } else {
                log.warn("Photo file to delete did not exist at {}", filePath);
            }
        } catch (IOException e) {
            log.error("Could not delete photo file at {}", filePath, e);
        }
    }

    private String determineExtension(String contentType, String originalFilename) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
