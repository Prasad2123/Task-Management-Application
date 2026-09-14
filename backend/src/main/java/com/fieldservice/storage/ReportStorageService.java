package com.fieldservice.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Storage abstraction for generated work reports (PDFs).
 * Decouples report binary storage from business logic, allowing seamless swapping
 * between local filesystem storage and cloud object storage (e.g. AWS S3 / MinIO).
 */
public interface ReportStorageService {

    /**
     * Stores the given report PDF data.
     *
     * @param workId      Work identifier
     * @param fileName    Safe file name
     * @param inputStream Stream of the PDF content
     * @param size        File size in bytes
     * @return Storage reference key (relative path or object key)
     */
    String store(Long workId, String fileName, InputStream inputStream, long size);

    /**
     * Loads the stored report as a Spring Resource for streaming.
     *
     * @param storageReference Storage reference key returned by {@link #store}
     * @return Resource representing the file
     */
    Resource loadAsResource(String storageReference);

    /**
     * Deletes the stored report file.
     *
     * @param storageReference Storage reference key returned by {@link #store}
     */
    void delete(String storageReference);
}
