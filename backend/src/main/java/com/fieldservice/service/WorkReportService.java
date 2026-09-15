package com.fieldservice.service;

import com.fieldservice.dto.WorkReportDto;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.BadRequestException;
import com.fieldservice.exception.ResourceNotFoundException;
import com.fieldservice.repository.*;
import com.fieldservice.storage.PhotoStorageService;
import com.fieldservice.storage.ReportStorageService;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReportService {

    private final WorkReportRepository workReportRepository;
    private final WorkRepository workRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final AdditionalWorkRepository additionalWorkRepository;
    private final WorkPhotoRepository workPhotoRepository;
    private final ApprovalRepository approvalRepository;
    private final ActivityEventRepository activityEventRepository;
    private final PhotoStorageService photoStorageService;
    private final ReportStorageService reportStorageService;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final Color COLOR_PRIMARY = new Color(24, 76, 120);     // Deep Corporate Blue
    private static final Color COLOR_SECONDARY = new Color(70, 130, 180); // Steel Blue
    private static final Color COLOR_SUCCESS = new Color(34, 139, 34);    // Forest Green
    private static final Color COLOR_BG_LIGHT = new Color(245, 248, 252); // Light Gray/Blue

    /**
     * Authoritative Work Report Generation.
     * Compiles all work metadata, location, timeline, approvals, checklist, additional work,
     * and photos into an authoritative PDF.
     */
    @Transactional
    public WorkReportDto generateReport(WorkEntity work) {
        if (work == null) {
            throw new IllegalArgumentException("Work cannot be null");
        }

        // Idempotency: If valid report already exists for this work, return it
        Optional<WorkReportEntity> existing = workReportRepository.findByWorkId(work.getId());
        if (existing.isPresent()) {
            log.info("Work report already exists for work {}, reusing report {}", work.getId(), existing.get().getReportNumber());
            return toDto(existing.get(), work);
        }

        long nowEpoch = System.currentTimeMillis();
        String reportNumber = String.format("REP-W%d-%d", work.getId(), nowEpoch);
        String fileName = String.format("WorkReport_%d.pdf", work.getId());

        // Fetch all sub-resources
        List<ChecklistItemEntity> checklist = checklistItemRepository.findByWorkIdOrderByDisplayOrderAscCreatedAtAsc(work.getId());
        List<AdditionalWorkEntity> additionalWork = additionalWorkRepository.findByWorkIdOrderByCreatedAtAsc(work.getId());
        List<WorkPhotoEntity> photos = workPhotoRepository.findByWorkIdOrderByCreatedAtAsc(work.getId());
        List<ApprovalEntity> approvals = approvalRepository.findByWorkIdOrderByCreatedAtAsc(work.getId());
        List<ActivityEventEntity> activityEvents = activityEventRepository.findByWorkIdOrderByEventTimestampAsc(work.getId());

        // Calculate authoritative duration
        String durationStr = calculateDuration(work.getStartTime(), work.getCompletedAt());

        // Render PDF in-memory
        byte[] pdfBytes = renderPdf(work, checklist, additionalWork, photos, approvals, activityEvents, reportNumber, durationStr);

        // Store PDF file via storage service
        String storageRef = reportStorageService.store(work.getId(), fileName, new ByteArrayInputStream(pdfBytes), pdfBytes.length);

        // Save DB record
        WorkReportEntity entity = WorkReportEntity.builder()
                .work(work)
                .reportNumber(reportNumber)
                .storageReference(storageRef)
                .fileName(fileName)
                .contentType("application/pdf")
                .fileSize((long) pdfBytes.length)
                .generatedAt(Instant.now())
                .createdBy(work.getServiceBoy())
                .version(1)
                .build();

        entity = workReportRepository.save(entity);
        log.info("Generated and stored work report {} for work {} (size: {} bytes)", reportNumber, work.getId(), pdfBytes.length);

        return toDto(entity, work);
    }

    /**
     * Retrieves report metadata with strict role & ownership authorization.
     */
    @Transactional
    public WorkReportDto getReportMetadata(Long workId, UserEntity currentUser) {
        WorkEntity work = findAndAuthorizeWork(workId, currentUser);

        if (work.getStatus() != WorkStatus.COMPLETED) {
            throw new BadRequestException("Work is not yet COMPLETED. Report is only available after final completion.");
        }

        WorkReportEntity report = workReportRepository.findByWorkId(workId)
                .orElseGet(() -> {
                    // Lazy auto-generate if missing
                    generateReport(work);
                    return workReportRepository.findByWorkId(workId)
                            .orElseThrow(() -> new ResourceNotFoundException("Failed to locate generated report for work " + workId));
                });

        return toDto(report, work);
    }

    /**
     * Loads report PDF resource for streaming with strict authorization.
     */
    @Transactional(readOnly = true)
    public Resource getReportPdfResource(Long workId, UserEntity currentUser) {
        WorkEntity work = findAndAuthorizeWork(workId, currentUser);

        if (work.getStatus() != WorkStatus.COMPLETED) {
            throw new BadRequestException("Work is not yet COMPLETED. Report is only available after final completion.");
        }

        WorkReportEntity report = workReportRepository.findByWorkId(workId)
                .orElseThrow(() -> new ResourceNotFoundException("Work report not found for work " + workId));

        return reportStorageService.loadAsResource(report.getStorageReference());
    }

    // -------------------------------------------------------
    // Security & Authorization Helpers
    // -------------------------------------------------------

    private WorkEntity findAndAuthorizeWork(Long workId, UserEntity currentUser) {
        WorkEntity work = workRepository.findById(workId)
                .orElseThrow(() -> new ResourceNotFoundException("Work not found with ID: " + workId));

        boolean isAssignedServiceBoy = work.getServiceBoy() != null && work.getServiceBoy().getId().equals(currentUser.getId());
        boolean isAssignedPoc = work.getPoc() != null && work.getPoc().getId().equals(currentUser.getId());
        boolean isAssignedSupervisor = work.getSupervisor() != null && work.getSupervisor().getId().equals(currentUser.getId());

        if (!isAssignedServiceBoy && !isAssignedPoc && !isAssignedSupervisor) {
            log.warn("Unauthorized report access attempt for work {} by user {} ({})", workId, currentUser.getId(), currentUser.getRole());
            throw new AccessDeniedException("You are not authorized to view or download the report for this work.");
        }

        return work;
    }

    private String calculateDuration(Instant start, Instant end) {
        if (start == null || end == null) return "N/A";
        Duration d = Duration.between(start, end);
        if (d.isNegative()) return "0m 0s";
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else {
            return String.format("%dm %ds", minutes, seconds);
        }
    }

    private WorkReportDto toDto(WorkReportEntity entity, WorkEntity work) {
        String duration = calculateDuration(work.getStartTime(), work.getCompletedAt());
        String compAt = work.getCompletedAt() != null ? TIME_FORMATTER.format(work.getCompletedAt()) : "N/A";
        String genAt = entity.getGeneratedAt() != null ? TIME_FORMATTER.format(entity.getGeneratedAt()) : "N/A";

        return WorkReportDto.builder()
                .id(entity.getId())
                .workId(work.getId())
                .reportNumber(entity.getReportNumber())
                .fileName(entity.getFileName())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .generatedAt(genAt)
                .version(entity.getVersion())
                .downloadUrl("/api/works/" + work.getId() + "/report/download")
                .duration(duration)
                .workTitle(work.getTitle())
                .clientName(work.getCompanyName())
                .completedAt(compAt)
                .build();
    }

    // -------------------------------------------------------
    // PDF Generation Engine (OpenPDF)
    // -------------------------------------------------------

    private byte[] renderPdf(
            WorkEntity work,
            List<ChecklistItemEntity> checklist,
            List<AdditionalWorkEntity> additionalWork,
            List<WorkPhotoEntity> photos,
            List<ApprovalEntity> approvals,
            List<ActivityEventEntity> activityEvents,
            String reportNumber,
            String durationStr
    ) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, COLOR_PRIMARY);
            Font fontSubtitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_SECONDARY);
            Font fontSectionHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, COLOR_PRIMARY);
            Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
            Font fontValue = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            Font fontSuccess = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_SUCCESS);

            // 1. Header Banner
            Paragraph title = new Paragraph("FIELD SERVICE MANAGEMENT", fontTitle);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("Official Work Completion & Evidence Report", fontSubtitle);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(15);
            document.add(subtitle);

            // Report Meta Badge Table
            PdfPTable metaTable = new PdfPTable(4);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{25, 25, 25, 25});
            metaTable.setSpacingAfter(15);

            addCell(metaTable, "Report Number", reportNumber, fontLabel, fontValue);
            addCell(metaTable, "Work ID", "WORK-" + work.getId(), fontLabel, fontValue);
            addCell(metaTable, "Status", "✓ COMPLETED", fontLabel, fontSuccess);
            addCell(metaTable, "Total Duration", durationStr, fontLabel, fontValue);
            document.add(metaTable);

            // 2. Work & Site Details Table
            document.add(new Paragraph("1. Work & Location Details", fontSectionHeader));
            PdfPTable workTable = new PdfPTable(2);
            workTable.setWidthPercentage(100);
            workTable.setWidths(new float[]{50, 50});
            workTable.setSpacingBefore(6);
            workTable.setSpacingAfter(15);

            addCell(workTable, "Work Title", work.getTitle(), fontLabel, fontValue);
            addCell(workTable, "Client / Company", work.getCompanyName() != null ? work.getCompanyName() : "N/A", fontLabel, fontValue);
            addCell(workTable, "Site Address", work.getAddress() != null ? work.getAddress() : "N/A", fontLabel, fontValue);
            addCell(workTable, "Scheduled Date", work.getScheduledDate() != null ? work.getScheduledDate().toString() : "N/A", fontLabel, fontValue);

            String startedAt = work.getStartTime() != null ? TIME_FORMATTER.format(work.getStartTime()) : "N/A";
            String completedAt = work.getCompletedAt() != null ? TIME_FORMATTER.format(work.getCompletedAt()) : "N/A";
            addCell(workTable, "Started Time (Server Auth)", startedAt, fontLabel, fontValue);
            addCell(workTable, "Completed Time (Server Auth)", completedAt, fontLabel, fontValue);

            String coords = (work.getLatitude() != null && work.getLongitude() != null)
                    ? String.format("%.5f, %.5f (Allowed Radius: %.0fm)", work.getLatitude(), work.getLongitude(), work.getAllowedRadiusMeters())
                    : "Not specified";
            addCell(workTable, "GPS Site Geofence", coords, fontLabel, fontValue);
            addCell(workTable, "Start Location Verification", "✓ Verified on-site via Haversine validation", fontLabel, fontSuccess);
            document.add(workTable);

            // 3. Personnel Table
            document.add(new Paragraph("2. Personnel & Stakeholders", fontSectionHeader));
            PdfPTable userTable = new PdfPTable(3);
            userTable.setWidthPercentage(100);
            userTable.setWidths(new float[]{33, 33, 34});
            userTable.setSpacingBefore(6);
            userTable.setSpacingAfter(15);

            String sbInfo = work.getServiceBoy() != null ? work.getServiceBoy().getName() + "\n(" + work.getServiceBoy().getEmail() + ")" : "N/A";
            String pocInfo = work.getPoc() != null ? work.getPoc().getName() + "\n(" + work.getPoc().getEmail() + ")" : "N/A";
            String supInfo = work.getSupervisor() != null ? work.getSupervisor().getName() + "\n(" + work.getSupervisor().getEmail() + ")" : "N/A";

            addCell(userTable, "Service Technician", sbInfo, fontLabel, fontValue);
            addCell(userTable, "Person of Contact (POC)", pocInfo, fontLabel, fontValue);
            addCell(userTable, "Site Supervisor", supInfo, fontLabel, fontValue);
            document.add(userTable);

            // 4. Multi-Stage Approvals Table
            document.add(new Paragraph("3. Authoritative Multi-Stage Approvals", fontSectionHeader));
            PdfPTable appTable = new PdfPTable(4);
            appTable.setWidthPercentage(100);
            appTable.setWidths(new float[]{25, 25, 25, 25});
            appTable.setSpacingBefore(6);
            appTable.setSpacingAfter(15);

            addTableHeader(appTable, new String[]{"Stage", "Reviewer", "Decision", "Timestamp"}, fontLabel);
            for (ApprovalEntity app : approvals) {
                String stage = app.getApproverRole() != null ? app.getApproverRole().name() : "N/A";
                String reviewer = app.getApprover() != null ? app.getApprover().getName() : "System";
                String decision = app.getStatus() != null ? app.getStatus().name() : "PENDING";
                String timestamp = app.getDecidedAt() != null ? TIME_FORMATTER.format(app.getDecidedAt()) : "Pending";

                appTable.addCell(createCell(stage, fontValue));
                appTable.addCell(createCell(reviewer, fontValue));
                appTable.addCell(createCell(decision.equals("APPROVED") ? "✓ " + decision : decision, decision.equals("APPROVED") ? fontSuccess : fontValue));
                appTable.addCell(createCell(timestamp, fontValue));
            }
            if (approvals.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No formal approval records found.", fontValue));
                emptyCell.setColspan(4);
                emptyCell.setPadding(8);
                appTable.addCell(emptyCell);
            }
            document.add(appTable);

            // 5. Checklist Execution Table
            document.add(new Paragraph("4. Predefined / Assigned Checklist Execution", fontSectionHeader));
            PdfPTable chkTable = new PdfPTable(3);
            chkTable.setWidthPercentage(100);
            chkTable.setWidths(new float[]{50, 25, 25});
            chkTable.setSpacingBefore(6);
            chkTable.setSpacingAfter(15);

            addTableHeader(chkTable, new String[]{"Task Description", "Status", "Completed At"}, fontLabel);
            for (ChecklistItemEntity item : checklist) {
                String statusText = item.isCompleted() ? "✓ Completed" : "Pending";
                String compTime = item.getCompletedAt() != null ? TIME_FORMATTER.format(item.getCompletedAt()) : "-";
                chkTable.addCell(createCell(item.getTitle(), fontValue));
                chkTable.addCell(createCell(statusText, item.isCompleted() ? fontSuccess : fontValue));
                chkTable.addCell(createCell(compTime, fontValue));
            }
            if (checklist.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No checklist items assigned.", fontValue));
                emptyCell.setColspan(3);
                emptyCell.setPadding(8);
                chkTable.addCell(emptyCell);
            }
            document.add(chkTable);

            // 6. Additional Work Items Table
            document.add(new Paragraph("5. Additional Work Performed", fontSectionHeader));
            PdfPTable addTable = new PdfPTable(3);
            addTable.setWidthPercentage(100);
            addTable.setWidths(new float[]{50, 25, 25});
            addTable.setSpacingBefore(6);
            addTable.setSpacingAfter(15);

            addTableHeader(addTable, new String[]{"Additional Task / Description", "Performed By", "Recorded At"}, fontLabel);
            for (AdditionalWorkEntity aw : additionalWork) {
                String creator = aw.getCreatedBy() != null ? aw.getCreatedBy().getName() : "Technician";
                String cTime = aw.getCreatedAt() != null ? TIME_FORMATTER.format(aw.getCreatedAt()) : "-";
                addTable.addCell(createCell(aw.getDescription(), fontValue));
                addTable.addCell(createCell(creator, fontValue));
                addTable.addCell(createCell(cTime, fontValue));
            }
            if (additionalWork.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No additional work performed for this work.", fontValue));
                emptyCell.setColspan(3);
                emptyCell.setPadding(8);
                addTable.addCell(emptyCell);
            }
            document.add(addTable);

            // 7. Chronological Activity Timeline Table
            document.add(new Paragraph("6. Authoritative Activity Audit Timeline", fontSectionHeader));
            PdfPTable timelineTable = new PdfPTable(3);
            timelineTable.setWidthPercentage(100);
            timelineTable.setWidths(new float[]{25, 25, 50});
            timelineTable.setSpacingBefore(6);
            timelineTable.setSpacingAfter(15);

            addTableHeader(timelineTable, new String[]{"Timestamp", "Event Type", "Event Description & Actor"}, fontLabel);
            for (ActivityEventEntity event : activityEvents) {
                String eTime = event.getCreatedAt() != null ? TIME_FORMATTER.format(event.getCreatedAt()) : "-";
                String eType = event.getEventType() != null ? event.getEventType().name() : "EVENT";
                timelineTable.addCell(createCell(eTime, fontValue));
                timelineTable.addCell(createCell(eType, fontValue));
                timelineTable.addCell(createCell(event.getDescription(), fontValue));
            }
            if (activityEvents.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No activity events recorded.", fontValue));
                emptyCell.setColspan(3);
                emptyCell.setPadding(8);
                timelineTable.addCell(emptyCell);
            }
            document.add(timelineTable);

            // 8. Photo Evidence Gallery
            document.add(new Paragraph("7. Photographic Work Evidence", fontSectionHeader));
            Paragraph photoNote = new Paragraph("Photographs captured and authoritative timestamps recorded via Field Service Mobile Client:", fontValue);
            photoNote.setSpacingAfter(10);
            document.add(photoNote);

            if (photos.isEmpty()) {
                Paragraph noPhotos = new Paragraph("No photographic evidence uploaded for this work.", fontValue);
                document.add(noPhotos);
            } else {
                PdfPTable photoGrid = new PdfPTable(2);
                photoGrid.setWidthPercentage(100);
                photoGrid.setWidths(new float[]{50, 50});
                photoGrid.setSpacingAfter(15);

                for (WorkPhotoEntity photo : photos) {
                    PdfPCell cell = new PdfPCell();
                    cell.setPadding(8);
                    cell.setBackgroundColor(COLOR_BG_LIGHT);
                    cell.setBorderColor(new Color(220, 225, 230));

                    // Attempt to embed actual photo
                    boolean embedded = false;
                    try {
                        if (photo.getStorageReference() != null) {
                            Resource res = photoStorageService.loadAsResource(photo.getStorageReference());
                            if (res != null && res.exists()) {
                                try (InputStream is = res.getInputStream()) {
                                    byte[] imgBytes = is.readAllBytes();
                                    if (imgBytes.length > 0) {
                                        Image img = Image.getInstance(imgBytes);
                                        img.scaleToFit(220, 150);
                                        img.setAlignment(Element.ALIGN_CENTER);
                                        cell.addElement(img);
                                        embedded = true;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Could not embed image {} in report: {}", photo.getId(), e.getMessage());
                    }

                    if (!embedded) {
                        Paragraph placeholder = new Paragraph("[Photo Evidence on file]\n" + photo.getTitle(), fontLabel);
                        placeholder.setAlignment(Element.ALIGN_CENTER);
                        cell.addElement(placeholder);
                    }

                    // Metadata caption
                    String pDate = photo.getCreatedAt() != null ? TIME_FORMATTER.format(photo.getCreatedAt()) : "N/A";
                    String cat = photo.getCategory() != null ? photo.getCategory().name() : "GENERAL";
                    Paragraph caption = new Paragraph(
                            photo.getTitle() + " (" + cat + ")\nUploaded: " + pDate + (photo.getCaption() != null ? "\nNote: " + photo.getCaption() : ""),
                            fontValue
                    );
                    caption.setSpacingBefore(4);
                    cell.addElement(caption);

                    photoGrid.addCell(cell);
                }

                // Complete row if odd count
                if (photos.size() % 2 != 0) {
                    PdfPCell empty = new PdfPCell();
                    empty.setBorder(Rectangle.NO_BORDER);
                    photoGrid.addCell(empty);
                }
                document.add(photoGrid);
            }

            // Footer note
            Paragraph footer = new Paragraph(
                    "This document is an authoritative, digitally compiled field service completion report.\nGenerated by Field Service Management Engine on " + TIME_FORMATTER.format(Instant.now()),
                    fontLabel
            );
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(20);
            document.add(footer);

            document.close();
        } catch (Exception e) {
            log.error("Fatal error generating PDF for work {}", work.getId(), e);
            throw new IllegalStateException("Failed to generate work report PDF: " + e.getMessage(), e);
        }

        return out.toByteArray();
    }

    private void addCell(PdfPTable table, String label, String value, Font fontLabel, Font fontValue) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(6);
        cell.setBackgroundColor(COLOR_BG_LIGHT);
        cell.setBorderColor(new Color(220, 225, 230));
        cell.addElement(new Paragraph(label, fontLabel));
        cell.addElement(new Paragraph(value != null ? value : "-", fontValue));
        table.addCell(cell);
    }

    private void addTableHeader(PdfPTable table, String[] headers, Font font) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font));
            cell.setPadding(6);
            cell.setBackgroundColor(new Color(230, 235, 245));
            cell.setBorderColor(new Color(200, 210, 220));
            table.addCell(cell);
        }
    }

    private PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setPadding(6);
        cell.setBorderColor(new Color(225, 230, 235));
        return cell;
    }
}
