package com.example.taskmanagementapplication.core.model

enum class WorkStatus {
    NOT_STARTED,
    WORK_STARTED,
    IN_PROGRESS,
    WAITING_FOR_REVIEW,
    WAITING_FOR_POC_REVIEW,
    WAITING_FOR_SUPERVISOR_REVIEW,
    APPROVED,
    COMPLETED,
    REJECTED
}

data class ChecklistItem(
    val id: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val isAdditional: Boolean = false,
    val completedAt: String? = null,
    val createdAt: String? = null
)

data class ActivityEvent(
    val id: String,
    val description: String,
    val timestamp: String,
    val isDone: Boolean = true
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
    val gradientSeed: Int = 0
)

enum class ApprovalState {
    PENDING,
    APPROVED,
    REJECTED
}

data class Work(
    val id: String,
    val title: String,
    val companyName: String,
    val address: String,
    val serviceBoyName: String,
    val pocName: String,
    val supervisorName: String,
    val status: WorkStatus,
    val scheduledDate: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val notes: String? = null,
    val description: String = "",
    val distance: String = "2.4 km away",
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
    val completedAt: String? = null
) {
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
    val relatedWorkId: String? = "W001"
)


