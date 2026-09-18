package com.example.taskmanagementapplication.core.mock

import com.example.taskmanagementapplication.core.model.ActivityEvent
import com.example.taskmanagementapplication.core.model.ChecklistItem
import com.example.taskmanagementapplication.core.model.PhotoCategory
import com.example.taskmanagementapplication.core.model.PhotoUploadStatus
import com.example.taskmanagementapplication.core.model.Work
import com.example.taskmanagementapplication.core.model.WorkPhoto
import com.example.taskmanagementapplication.core.model.WorkStatus

object MockWorkRepository {

    val defaultChecklist = listOf(
        ChecklistItem(
            id = "C1",
            title = "General Site Inspection",
            description = "Initial inspection of common areas and perimeter for pest activity",
            isCompleted = true,
            isAdditional = false,
            completedAt = "10:32 AM"
        ),
        ChecklistItem(
            id = "C2",
            title = "Pest Control Treatment",
            description = "Apply approved chemical treatment as per schedule",
            isCompleted = true,
            isAdditional = false,
            completedAt = "10:48 AM"
        ),
        ChecklistItem(
            id = "C3",
            title = "Equipment Inspection",
            description = "Inspect spray pumps and safety equipment condition",
            isCompleted = false,
            isAdditional = false
        ),
        ChecklistItem(
            id = "C4",
            title = "Preventive Maintenance Check",
            description = "Inspect bait stations and replace consumable traps",
            isCompleted = false,
            isAdditional = false
        ),
        ChecklistItem(
            id = "C5",
            title = "Safety Inspection",
            description = "Verify proper PPE, warning signage and chemical storage",
            isCompleted = false,
            isAdditional = false
        ),
        ChecklistItem(
            id = "C6",
            title = "Area Cleaning",
            description = "Clean treated zones and safely dispose of packaging waste",
            isCompleted = false,
            isAdditional = false
        )
    )

    val defaultPhotos = listOf(
        WorkPhoto(
            id = "P1",
            title = "Perimeter Inspection",
            category = PhotoCategory.SITE_INSPECTION,
            uploadedAt = "10:35 AM",
            uploadStatus = PhotoUploadStatus.UPLOADED,
            caption = "Perimeter fence and entry check complete",
            gradientSeed = 1
        ),
        WorkPhoto(
            id = "P2",
            title = "Chemical Solution Prep",
            category = PhotoCategory.TREATMENT_APPLICATION,
            uploadedAt = "10:50 AM",
            uploadStatus = PhotoUploadStatus.UPLOADED,
            caption = "Approved formulation mixed as per standard protocol",
            gradientSeed = 2
        ),
        WorkPhoto(
            id = "P3",
            title = "Sprayer Pressure Gauge",
            category = PhotoCategory.EQUIPMENT_CHECK,
            uploadedAt = "11:15 AM",
            uploadStatus = PhotoUploadStatus.UPLOADED,
            caption = "Pump operating at 40 PSI normal range",
            gradientSeed = 3
        ),
        WorkPhoto(
            id = "P4",
            title = "Bait Station Replacement",
            category = PhotoCategory.ADDITIONAL_WORK,
            uploadedAt = "11:38 AM",
            uploadStatus = PhotoUploadStatus.UPLOADING,
            caption = "Damaged station replaced with new bait unit",
            uploadProgress = 0.65f,
            gradientSeed = 4
        ),
        WorkPhoto(
            id = "P5",
            title = "Exhaust Vent Intake",
            category = PhotoCategory.SAFETY_PPE,
            uploadedAt = "11:41 AM",
            uploadStatus = PhotoUploadStatus.FAILED,
            caption = "High residue area flagged for safety review",
            uploadProgress = 0.3f,
            gradientSeed = 5
        )
    )

    val demoWork = Work(
        id = "W001",
        title = "Monthly Pest Control Service",
        companyName = "ABC Industrial Services",
        address = "Plot No. 45, Industrial Estate, Andheri East, Mumbai - 400093",
        serviceBoyName = "Rahul Patil",
        pocName = "Amit Sharma",
        supervisorName = "Suresh Patil",
        status = WorkStatus.NOT_STARTED,
        scheduledDate = "12 Sep 2026",
        notes = "Please carry the standard pest control kit. Access through Gate B.",
        description = "Perform scheduled monthly inspection, treatment and preventive maintenance activities at the assigned location. Ensure all safety protocols are followed.",
        distance = "2.4 km away",
        latitude = 19.1136,
        longitude = 72.8697,
        allowedRadiusMeters = 150.0,
        backendId = 1L,
        checklist = defaultChecklist,
        activityLog = listOf(
            ActivityEvent("A1", "Work session started", "10:30 AM"),
            ActivityEvent("A2", "General Site Inspection completed", "10:32 AM"),
            ActivityEvent("A3", "Pest Control Treatment completed", "10:48 AM")
        ),
        photos = defaultPhotos
    )

    val pocPendingWork = demoWork.copy(
        status = WorkStatus.WAITING_FOR_POC_REVIEW
    )

    fun getWorkForServiceBoy(): Work = demoWork
    fun getWorkForPoc(): Work = pocPendingWork
}
