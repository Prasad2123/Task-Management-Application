package com.example.taskmanagementapplication.core.model

enum class WorkStatus {
    NOT_STARTED,
    WORK_STARTED,
    IN_PROGRESS,
    WAITING_FOR_REVIEW,
    WAITING_FOR_POC_REVIEW,
    WAITING_FOR_SUPERVISOR_REVIEW,
    APPROVED,          // Maps to SUPERVISOR_APPROVED from backend
    COMPLETED,
    REJECTED
}

data class MasterTask(
    val id: Long,
    val taskLabel: String,
    val category: String = "GENERAL",
    val displayOrder: Int = 0,
    val isActive: Boolean = true
)

data class ChecklistItem(
    val id: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val isAdditional: Boolean = false,
    val completedAt: String? = null,
    val completedById: Long? = null,
    val completedByName: String? = null,
    val masterTaskId: Long? = null,
    val taskLabel: String? = null,
    val displayOrder: Int = 0,
    val createdAt: String? = null
)

data class ActivityEvent(
    val id: String,
    val description: String,
    val timestamp: String,
    val isDone: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null
)

enum class PhotoUploadStatus {
    UPLOADING,
    UPLOADED,
    FAILED,
    PENDING
}

enum class PhotoCategory(val displayName: String) {
    SITE_INSPECTION("Site Inspection"),
    TREATMENT_APPLICATION("Treatment Application"),
    EQUIPMENT_CHECK("Equipment Check"),
    SAFETY_PPE("Safety & PPE"),
    ADDITIONAL_WORK("Additional Work"),
    GENERAL("General Evidence")
}

data class WorkPhoto(
    val id: String,
    val title: String,
    val category: PhotoCategory = PhotoCategory.GENERAL,
    val uploadedAt: String,
    val uploadStatus: PhotoUploadStatus = PhotoUploadStatus.UPLOADED,
    val caption: String? = null,
    val uploadProgress: Float = 1.0f,
    val isSelected: Boolean = false,
    val gradientSeed: Int = 0,
    val localUri: String? = null,
    val remoteUrl: String? = null,
    val backendId: Long? = null
)

enum class ApprovalState {
    PENDING,
    APPROVED,
    REJECTED
}

/**
 * Additional/extra work item — backed by backend additional_works table.
 */
data class AdditionalWorkItem(
    val id: String,
    val workId: String,
    val description: String,
    val masterTaskId: Long? = null,
    val taskLabel: String? = null,
    val createdById: Long? = null,
    val createdByName: String = "",
    val createdAt: String = ""
)

data class Work(
    val id: String,
    val title: String,
    val companyName: String,
    val address: String,
    val serviceBoyName: String,
    val serviceBoyPhone: String? = null,
    val serviceBoyEmail: String? = null,
    val pocName: String,
    val pocPhone: String? = null,
    val pocEmail: String? = null,
    val supervisorName: String,
    val supervisorPhone: String? = null,
    val supervisorEmail: String? = null,
    val status: WorkStatus,
    val scheduledDate: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val notes: String? = null,
    val description: String = "",
    val distance: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val allowedRadiusMeters: Double = 150.0,
    val locationVerified: Boolean? = null,
    val distanceFromWorkMeters: Double? = null,
    val checklist: List<ChecklistItem> = emptyList(),
    val activityLog: List<ActivityEvent> = emptyList(),
    val photos: List<WorkPhoto> = emptyList(),
    val pocApproved: Boolean? = null,
    val pocApprovalTime: String? = null,
    val pocRejectionReason: String? = null,
    val supervisorApproved: Boolean? = null,
    val supervisorApprovalTime: String? = null,
    val supervisorRejectionReason: String? = null,
    val submittedForReviewAt: String? = null,
    val completedAt: String? = null,
    val readyForCompletion: Boolean = false,
    val googleMapsLink: String? = null,
    // Backend IDs for API calls
    val backendId: Long? = null,
    val serviceBoyId: Long? = null,
    val pocId: Long? = null,
    val supervisorId: Long? = null
) {
    val isReadyForCompletion: Boolean
        get() = readyForCompletion || (pocApproved == true && supervisorApproved == true && status != WorkStatus.COMPLETED)

    val pocApprovalState: ApprovalState
        get() = when (pocApproved) {
            true -> ApprovalState.APPROVED
            false -> ApprovalState.REJECTED
            null -> ApprovalState.PENDING
        }

    val supervisorApprovalState: ApprovalState
        get() = when (supervisorApproved) {
            true -> ApprovalState.APPROVED
            false -> ApprovalState.REJECTED
            null -> ApprovalState.PENDING
        }
}

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean = false,
    val relatedWorkId: String? = null
)
