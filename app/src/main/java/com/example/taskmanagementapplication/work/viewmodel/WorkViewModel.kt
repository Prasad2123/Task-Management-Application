package com.example.taskmanagementapplication.work.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanagementapplication.core.mock.MockWorkRepository
import com.example.taskmanagementapplication.core.model.ActivityEvent
import com.example.taskmanagementapplication.core.model.AppNotification
import com.example.taskmanagementapplication.core.model.ChecklistItem
import com.example.taskmanagementapplication.core.model.PhotoCategory
import com.example.taskmanagementapplication.core.model.PhotoUploadStatus
import com.example.taskmanagementapplication.core.model.Work
import com.example.taskmanagementapplication.core.model.WorkPhoto
import com.example.taskmanagementapplication.core.model.WorkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkViewModel : ViewModel() {

    private val _work = MutableStateFlow(MockWorkRepository.getWorkForServiceBoy())
    val work: StateFlow<Work> = _work.asStateFlow()

    // Timer — seconds elapsed since work started
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var startTimestamp: String = ""

    // -------------------------------------------------------
    // Work lifecycle
    // -------------------------------------------------------

    fun startWork() {
        if (_work.value.status != WorkStatus.NOT_STARTED && _work.value.status != WorkStatus.WORK_STARTED) {
            return
        }
        startTimestamp = currentTimeString()
        _work.update { it.copy(status = WorkStatus.IN_PROGRESS, startTime = startTimestamp) }
        addActivity("Work session started", startTimestamp)
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1000L)
                _elapsedSeconds.update { it + 1 }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    // -------------------------------------------------------
    // Checklist & Additional Work
    // -------------------------------------------------------

    fun toggleChecklistItem(itemId: String) {
        val now = currentTimeString()
        var toggledItem: ChecklistItem? = null
        var nowCompleted = false

        val updatedList = _work.value.checklist.map { item ->
            if (item.id == itemId) {
                val nextState = !item.isCompleted
                nowCompleted = nextState
                toggledItem = item
                item.copy(
                    isCompleted = nextState,
                    completedAt = if (nextState) now else null
                )
            } else {
                item
            }
        }
        _work.update { it.copy(checklist = updatedList) }

        // Log the activity
        toggledItem?.let { item ->
            val eventDesc = if (nowCompleted) {
                "${item.title} completed"
            } else {
                "${item.title} marked incomplete"
            }
            addActivity(eventDesc, now)
        }
    }

    fun addAdditionalWork(title: String, description: String = ""): Boolean {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return false

        val now = currentTimeString()
        val newItem = ChecklistItem(
            id = "ADD_${System.currentTimeMillis()}",
            title = trimmedTitle,
            description = description.trim(),
            isCompleted = false,
            isAdditional = true,
            createdAt = now
        )

        _work.update { it.copy(checklist = it.checklist + newItem) }
        addActivity("Additional work added: \"$trimmedTitle\"", now)
        return true
    }

    fun editAdditionalWork(itemId: String, newTitle: String, newDescription: String = ""): Boolean {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isBlank()) return false

        val now = currentTimeString()
        var oldItem: ChecklistItem? = null

        val updatedList = _work.value.checklist.map { item ->
            if (item.id == itemId && item.isAdditional) {
                oldItem = item
                item.copy(
                    title = trimmedTitle,
                    description = newDescription.trim()
                )
            } else {
                item
            }
        }

        if (oldItem != null) {
            _work.update { it.copy(checklist = updatedList) }
            addActivity("Additional work edited: \"$trimmedTitle\"", now)
            return true
        }
        return false
    }

    fun deleteAdditionalWork(itemId: String): Boolean {
        val now = currentTimeString()
        val itemToDelete = _work.value.checklist.find { it.id == itemId && it.isAdditional } ?: return false

        val updatedList = _work.value.checklist.filterNot { it.id == itemId }
        _work.update { it.copy(checklist = updatedList) }
        addActivity("Additional work removed: \"${itemToDelete.title}\"", now)
        return true
    }

    // -------------------------------------------------------
    // Activity log
    // -------------------------------------------------------

    private fun addActivity(description: String, timestamp: String) {
        val currentLog = _work.value.activityLog
        val lastEvent = currentLog.lastOrNull()
        if (lastEvent != null && lastEvent.description == description) {
            return
        }
        if (description.contains("approved", ignoreCase = true) ||
            description.contains("Work completed", ignoreCase = true) ||
            description.contains("Work session started", ignoreCase = true) ||
            description.contains("Work started", ignoreCase = true)
        ) {
            if (currentLog.any { it.description == description }) {
                return
            }
        }

        val event = ActivityEvent(
            id = System.currentTimeMillis().toString(),
            description = description,
            timestamp = timestamp
        )
        _work.update { it.copy(activityLog = it.activityLog + event) }
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private fun currentTimeString(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }

    fun formatElapsedTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            "%02d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    // -------------------------------------------------------
    // Progress calculation helpers
    // -------------------------------------------------------

    fun getPredefinedItems(work: Work): List<ChecklistItem> =
        work.checklist.filter { !it.isAdditional }

    fun getAdditionalItems(work: Work): List<ChecklistItem> =
        work.checklist.filter { it.isAdditional }

    fun getPredefinedCompletedCount(work: Work): Int =
        work.checklist.count { !it.isAdditional && it.isCompleted }

    fun getPredefinedTotalCount(work: Work): Int =
        work.checklist.count { !it.isAdditional }

    fun getAdditionalCompletedCount(work: Work): Int =
        work.checklist.count { it.isAdditional && it.isCompleted }

    fun getAdditionalTotalCount(work: Work): Int =
        work.checklist.count { it.isAdditional }

    fun getTotalCompletedCount(work: Work): Int =
        work.checklist.count { it.isCompleted }

    fun getTotalCount(work: Work): Int =
        work.checklist.size

    fun getProgressFraction(work: Work): Float {
        val total = work.checklist.size
        return if (total > 0) work.checklist.count { it.isCompleted }.toFloat() / total else 0f
    }

    // -------------------------------------------------------
    // Work Photos & Evidence Management
    // -------------------------------------------------------

    fun addPhotos(newPhotos: List<WorkPhoto>) {
        if (newPhotos.isEmpty()) return
        val now = currentTimeString()
        val photosWithUploading = newPhotos.map {
            it.copy(uploadStatus = PhotoUploadStatus.UPLOADING, uploadProgress = 0.45f)
        }
        _work.update { it.copy(photos = it.photos + photosWithUploading) }

        val countText = if (newPhotos.size == 1) "1 work photo" else "${newPhotos.size} work photos"
        addActivity("$countText added", now)

        viewModelScope.launch(Dispatchers.Default) {
            delay(1200L)
            val newlyAddedIds = newPhotos.map { it.id }.toSet()
            _work.update { currentWork ->
                val updatedPhotos = currentWork.photos.map { photo ->
                    if (photo.id in newlyAddedIds) {
                        photo.copy(uploadStatus = PhotoUploadStatus.UPLOADED, uploadProgress = 1.0f)
                    } else {
                        photo
                    }
                }
                currentWork.copy(photos = updatedPhotos)
            }
        }
    }

    fun deletePhoto(photoId: String): Boolean {
        val now = currentTimeString()
        val photoToDelete = _work.value.photos.find { it.id == photoId } ?: return false
        _work.update { it.copy(photos = it.photos.filterNot { photo -> photo.id == photoId }) }
        addActivity("Photo removed: \"${photoToDelete.title}\"", now)
        return true
    }

    fun retryPhotoUpload(photoId: String) {
        val now = currentTimeString()
        _work.update { currentWork ->
            val updated = currentWork.photos.map {
                if (it.id == photoId) it.copy(uploadStatus = PhotoUploadStatus.UPLOADING, uploadProgress = 0.5f) else it
            }
            currentWork.copy(photos = updated)
        }
        addActivity("Photo upload retried", now)

        viewModelScope.launch(Dispatchers.Default) {
            delay(1000L)
            _work.update { currentWork ->
                val updated = currentWork.photos.map {
                    if (it.id == photoId) it.copy(uploadStatus = PhotoUploadStatus.UPLOADED, uploadProgress = 1.0f) else it
                }
                currentWork.copy(photos = updated)
            }
        }
    }

    fun createMockPhoto(
        title: String,
        category: PhotoCategory,
        caption: String? = null
    ): WorkPhoto {
        val id = "PHOTO_${System.currentTimeMillis()}_${(100..999).random()}"
        val now = currentTimeString()
        return WorkPhoto(
            id = id,
            title = title.ifBlank { "Work Evidence" },
            category = category,
            uploadedAt = now,
            uploadStatus = PhotoUploadStatus.UPLOADING,
            caption = caption?.trim()?.takeIf { it.isNotBlank() },
            uploadProgress = 0.4f,
            gradientSeed = (0..5).random()
        )
    }

    // Photo count helpers
    fun getPhotos(work: Work): List<WorkPhoto> = work.photos

    fun getTotalPhotosCount(work: Work): Int = work.photos.size

    fun getUploadedPhotosCount(work: Work): Int =
        work.photos.count { it.uploadStatus == PhotoUploadStatus.UPLOADED }

    fun getUploadingPhotosCount(work: Work): Int =
        work.photos.count { it.uploadStatus == PhotoUploadStatus.UPLOADING }

    fun getFailedPhotosCount(work: Work): Int =
        work.photos.count { it.uploadStatus == PhotoUploadStatus.FAILED }

    fun getPendingPhotosCount(work: Work): Int =
        work.photos.count { it.uploadStatus == PhotoUploadStatus.PENDING }

    // -------------------------------------------------------
    // Notifications State & Actions
    // -------------------------------------------------------
    private val _notifications = MutableStateFlow<List<AppNotification>>(
        listOf(
            AppNotification(
                id = "N1",
                title = "Work in progress update",
                message = "Rahul Patil started Monthly Pest Control Service at ABC Industrial Services.",
                timestamp = "10:32 AM",
                isRead = false
            ),
            AppNotification(
                id = "N2",
                title = "New work evidence photos",
                message = "5 inspection and treatment photos uploaded for verification.",
                timestamp = "11:15 AM",
                isRead = false
            )
        )
    )
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    fun markNotificationRead(id: String) {
        _notifications.update { currentList ->
            currentList.map { if (it.id == id) it.copy(isRead = true) else it }
        }
    }

    // -------------------------------------------------------
    // Review and Approval Workflow
    // -------------------------------------------------------

    fun submitWorkForReview(): Boolean {
        val now = currentTimeString()
        _work.update {
            it.copy(
                status = WorkStatus.WAITING_FOR_POC_REVIEW,
                submittedForReviewAt = now,
                pocApproved = null,
                pocRejectionReason = null,
                supervisorApproved = null,
                supervisorRejectionReason = null
            )
        }
        addActivity("Work submitted for review", now)
        _notifications.update { current ->
            listOf(
                AppNotification(
                    id = "NOTIF_${System.currentTimeMillis()}",
                    title = "Work ready for review",
                    message = "Monthly Pest Control Service has been submitted by ${_work.value.serviceBoyName} for POC review.",
                    timestamp = now,
                    isRead = false
                )
            ) + current
        }
        return true
    }

    fun approveByPoc(notes: String = ""): Boolean {
        if (_work.value.pocApproved == true) {
            return true // Idempotent: already approved
        }
        val now = currentTimeString()
        _work.update {
            it.copy(
                status = WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW,
                pocApproved = true,
                pocApprovalTime = now,
                pocRejectionReason = null
            )
        }
        addActivity("POC approved work (${_work.value.pocName})", now)
        _notifications.update { current ->
            listOf(
                AppNotification(
                    id = "NOTIF_${System.currentTimeMillis()}",
                    title = "POC Approved Work",
                    message = "${_work.value.pocName} approved the work evidence. Awaiting Supervisor review.",
                    timestamp = now,
                    isRead = false
                )
            ) + current
        }
        return true
    }

    fun rejectByPoc(reason: String): Boolean {
        val trimmed = reason.trim()
        if (trimmed.isBlank()) return false
        val now = currentTimeString()
        _work.update {
            it.copy(
                status = WorkStatus.REJECTED,
                pocApproved = false,
                pocRejectionReason = trimmed
            )
        }
        addActivity("POC requested changes: \"$trimmed\"", now)
        _notifications.update { current ->
            listOf(
                AppNotification(
                    id = "NOTIF_${System.currentTimeMillis()}",
                    title = "Changes Requested by POC",
                    message = "POC ${_work.value.pocName}: \"$trimmed\"",
                    timestamp = now,
                    isRead = false
                )
            ) + current
        }
        return true
    }

    fun approveBySupervisor(notes: String = ""): Boolean {
        // Enforce dependency: Supervisor can ONLY approve after POC has approved
        if (_work.value.pocApproved != true) {
            return false
        }
        if (_work.value.supervisorApproved == true) {
            return true // Idempotent: already approved
        }
        val now = currentTimeString()
        _work.update {
            it.copy(
                status = WorkStatus.APPROVED,
                supervisorApproved = true,
                supervisorApprovalTime = now,
                supervisorRejectionReason = null
            )
        }
        addActivity("Supervisor approved work (${_work.value.supervisorName})", now)
        _notifications.update { current ->
            listOf(
                AppNotification(
                    id = "NOTIF_${System.currentTimeMillis()}",
                    title = "Work Approved!",
                    message = "Supervisor ${_work.value.supervisorName} gave final approval. Ready to complete work.",
                    timestamp = now,
                    isRead = false
                )
            ) + current
        }
        return true
    }

    fun rejectBySupervisor(reason: String): Boolean {
        val trimmed = reason.trim()
        if (trimmed.isBlank()) return false
        val now = currentTimeString()
        _work.update {
            it.copy(
                status = WorkStatus.REJECTED,
                supervisorApproved = false,
                supervisorRejectionReason = trimmed
            )
        }
        addActivity("Supervisor requested changes: \"$trimmed\"", now)
        _notifications.update { current ->
            listOf(
                AppNotification(
                    id = "NOTIF_${System.currentTimeMillis()}",
                    title = "Changes Requested by Supervisor",
                    message = "Supervisor ${_work.value.supervisorName}: \"$trimmed\"",
                    timestamp = now,
                    isRead = false
                )
            ) + current
        }
        return true
    }

    fun completeWork(): Boolean {
        // Enforce dependency: Can only be completed once both POC and Supervisor have approved
        if (_work.value.pocApproved != true || _work.value.supervisorApproved != true) {
            return false
        }
        if (_work.value.status == WorkStatus.COMPLETED) {
            return true // Idempotent
        }
        val now = currentTimeString()
        timerJob?.cancel()
        _work.update {
            it.copy(
                status = WorkStatus.COMPLETED,
                completedAt = now
            )
        }
        addActivity("Work completed and closed", now)
        return true
    }

    fun canCompleteWork(work: Work = _work.value): Boolean =
        work.pocApproved == true && work.supervisorApproved == true && work.status != WorkStatus.COMPLETED

    fun canSupervisorApprove(work: Work = _work.value): Boolean =
        work.pocApproved == true && work.supervisorApproved != true

    fun continueWorkAfterRejection() {
        val now = currentTimeString()
        _work.update {
            it.copy(
                status = WorkStatus.IN_PROGRESS
            )
        }
        addActivity("Work resumed to address review feedback", now)
    }

    // Status helpers
    fun isWaitingForReview(work: Work = _work.value): Boolean =
        work.status == WorkStatus.WAITING_FOR_POC_REVIEW ||
        work.status == WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW ||
        work.status == WorkStatus.WAITING_FOR_REVIEW

    fun isApproved(work: Work = _work.value): Boolean = work.status == WorkStatus.APPROVED

    fun isCompleted(work: Work = _work.value): Boolean = work.status == WorkStatus.COMPLETED

    fun isRejected(work: Work = _work.value): Boolean = work.status == WorkStatus.REJECTED
}
