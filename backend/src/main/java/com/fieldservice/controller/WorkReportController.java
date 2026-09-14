package com.fieldservice.controller;

import com.fieldservice.dto.WorkReportDto;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.security.SecurityUtils;
import com.fieldservice.service.WorkReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/works/{workId}/report")
@RequiredArgsConstructor
public class WorkReportController {

    private final WorkReportService workReportService;

    /**
     * GET /api/works/{workId}/report
     * Retrieve work report metadata.
     */
    @GetMapping
    public ResponseEntity<WorkReportDto> getReportMetadata(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        WorkReportDto report = workReportService.getReportMetadata(workId, currentUser);
        return ResponseEntity.ok(report);
    }

    /**
     * GET /api/works/{workId}/report/download
     * Stream authoritative PDF report binary for download/viewing.
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        WorkReportDto metadata = workReportService.getReportMetadata(workId, currentUser);
        Resource resource = workReportService.getReportPdfResource(workId, currentUser);

        String fileName = metadata.getFileName() != null ? metadata.getFileName() : ("WorkReport_" + workId + ".pdf");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", fileName);
        if (metadata.getFileSize() != null && metadata.getFileSize() > 0) {
            headers.setContentLength(metadata.getFileSize());
        }
        headers.setCacheControl("private, no-cache, no-store, must-revalidate");

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
