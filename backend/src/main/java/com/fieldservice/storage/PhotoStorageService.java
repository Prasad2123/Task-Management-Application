package com.fieldservice.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Storage abstraction for work photos.
 * Decouples photo binary storage from business logic, allowing seamless swapping
 * between local filesystem storage and cloud object storage (e.g. AWS S3 / MinIO).
 */
public interface PhotoStorageService {

    /**
     * Stores the given photo data.
     *
     * @param workId           Work identifier
     * @param originalFilename Original name of the uploaded file
     * @param contentType      MIME content type (must be validated prior to storage)
     * @param inputStream      Stream of the file content
     * @param size             File size in bytes
     * @return Storage reference key (relative path or object key)
     */
    String store(Long workId, String originalFilename, String contentType, InputStream inputStream, long size);

    /**
     * Loads the stored photo as a Spring Resource for streaming.
     *
     * @param storageReference Storage reference key returned by {@link #store}
     * @return Resource representing the file
     */
    Resource loadAsResource(String storageReference);

    /**
     * Deletes the stored photo file.
     *
     * @param storageReference Storage reference key returned by {@link #store}
     */
    void delete(String storageReference);
}
