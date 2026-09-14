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
import java.util.UUID;

/**
 * Filesystem-backed implementation of ReportStorageService.
 * Implements strict path normalization to prevent path traversal attacks.
 */
@Slf4j
@Service
public class FileSystemReportStorageService implements ReportStorageService {

    private final Path rootLocation;

    public FileSystemReportStorageService(@Value("${app.storage.reports-dir:storage/reports}") String storageDir) {
        this.rootLocation = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("Initialized report storage directory at: {}", rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize report storage directory at " + rootLocation, e);
        }
    }

    @Override
    public String store(Long workId, String fileName, InputStream inputStream, long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Cannot store empty report file");
        }

        String safeFileName = (fileName != null && fileName.endsWith(".pdf"))
                ? fileName
                : "report_" + UUID.randomUUID() + ".pdf";

        // Prevent path traversal in filename
        Path p = Paths.get(safeFileName).getFileName();
        safeFileName = p.toString();

        Path targetDir = rootLocation.resolve(String.valueOf(workId)).normalize();
        Path destinationFile = targetDir.resolve(safeFileName).normalize().toAbsolutePath();

        if (!destinationFile.getParent().equals(targetDir.toAbsolutePath())) {
            throw new SecurityException("Cannot store file outside target directory");
        }

        try {
            Files.createDirectories(targetDir);
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored report at {} (size: {} bytes)", destinationFile, destinationFile.toFile().length());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store report file " + safeFileName, e);
        }

        // Return relative storage key: "workId/safeFileName"
        return String.valueOf(workId) + "/" + safeFileName;
    }

    @Override
    public Resource loadAsResource(String storageReference) {
        if (storageReference == null || storageReference.isBlank()) {
            throw new IllegalArgumentException("Storage reference must not be null or blank");
        }

        Path filePath = rootLocation.resolve(storageReference).normalize().toAbsolutePath();

        // Enforce path traversal protection
        if (!filePath.startsWith(rootLocation)) {
            throw new SecurityException("Cannot access file outside root storage location");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Report file not found: " + storageReference);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Report file not found: " + storageReference);
        }
    }

    @Override
    public void delete(String storageReference) {
        if (storageReference == null || storageReference.isBlank()) return;

        Path filePath = rootLocation.resolve(storageReference).normalize().toAbsolutePath();
        if (!filePath.startsWith(rootLocation)) {
            throw new SecurityException("Cannot delete file outside root storage location");
        }

        try {
            Files.deleteIfExists(filePath);
            log.info("Deleted report file: {}", storageReference);
        } catch (IOException e) {
            log.warn("Failed to delete report file: {}", storageReference, e);
        }
    }
}
