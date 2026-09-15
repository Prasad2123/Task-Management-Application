package com.example.taskmanagementapplication.work.viewmodel

import com.example.taskmanagementapplication.core.model.PhotoCategory
import com.example.taskmanagementapplication.core.model.PhotoUploadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkViewModelTest {

    private lateinit var viewModel: WorkViewModel

    @Before
    fun setUp() {
        viewModel = WorkViewModel()
    }

    @Test
    fun testInitialChecklistState() {
        val work = viewModel.work.value
        val predefined = viewModel.getPredefinedItems(work)
        val additional = viewModel.getAdditionalItems(work)

        assertEquals(6, predefined.size)
        assertEquals(0, additional.size)
        assertEquals(2, viewModel.getPredefinedCompletedCount(work))
        assertEquals(6, viewModel.getPredefinedTotalCount(work))
        assertEquals(2, viewModel.getTotalCompletedCount(work))
        assertEquals(6, viewModel.getTotalCount(work))
    }

    @Test
    fun testToggleChecklistItem_completeAndIncomplete() {
        val workInitial = viewModel.work.value
        val pendingItem = workInitial.checklist.first { !it.isCompleted }

        // Complete the item
        viewModel.toggleChecklistItem(pendingItem.id)
        val workAfterComplete = viewModel.work.value
        val completedItem = workAfterComplete.checklist.first { it.id == pendingItem.id }

        assertTrue(completedItem.isCompleted)
        assertNotNull(completedItem.completedAt)
        assertEquals(3, viewModel.getTotalCompletedCount(workAfterComplete))

        // Activity log should have recorded completion
        val latestLog = workAfterComplete.activityLog.last()
        assertTrue(latestLog.description.contains("completed"))

        // Now toggle back to incomplete
        viewModel.toggleChecklistItem(pendingItem.id)
        val workAfterUncheck = viewModel.work.value
        val uncheckedItem = workAfterUncheck.checklist.first { it.id == pendingItem.id }

        assertFalse(uncheckedItem.isCompleted)
        assertNull(uncheckedItem.completedAt)
        assertEquals(2, viewModel.getTotalCompletedCount(workAfterUncheck))

        val uncheckLog = workAfterUncheck.activityLog.last()
        assertTrue(uncheckLog.description.contains("marked incomplete"))
    }

    @Test
    fun testAddEditDeleteAdditionalWork() {
        // 1. Add additional work
        val addSuccess = viewModel.addAdditionalWork(
            title = "Repaired leakage near pump room",
            description = "Replaced fitting"
        )
        assertTrue(addSuccess)

        val workAfterAdd = viewModel.work.value
        val additionalItems = viewModel.getAdditionalItems(workAfterAdd)
        assertEquals(1, additionalItems.size)
        assertEquals(7, viewModel.getTotalCount(workAfterAdd))

        val addedItem = additionalItems.first()
        assertEquals("Repaired leakage near pump room", addedItem.title)
        assertTrue(addedItem.isAdditional)
        assertFalse(addedItem.isCompleted)
        assertNotNull(addedItem.createdAt)

        // 2. Reject blank additional work
        val emptyAddSuccess = viewModel.addAdditionalWork("   ")
        assertFalse(emptyAddSuccess)
        assertEquals(1, viewModel.getAdditionalItems(viewModel.work.value).size)

        // 3. Edit additional work
        val editSuccess = viewModel.editAdditionalWork(
            itemId = addedItem.id,
            newTitle = "Replaced pump valve and repaired leakage",
            newDescription = "Brass valve"
        )
        assertTrue(editSuccess)

        val workAfterEdit = viewModel.work.value
        val editedItem = viewModel.getAdditionalItems(workAfterEdit).first()
        assertEquals("Replaced pump valve and repaired leakage", editedItem.title)
        assertEquals("Brass valve", editedItem.description)

        // 4. Complete additional work
        viewModel.toggleChecklistItem(editedItem.id)
        val workAfterToggle = viewModel.work.value
        val toggledAdditional = viewModel.getAdditionalItems(workAfterToggle).first()
        assertTrue(toggledAdditional.isCompleted)
        assertEquals(1, viewModel.getAdditionalCompletedCount(workAfterToggle))
        assertEquals(3, viewModel.getTotalCompletedCount(workAfterToggle))

        // 5. Delete additional work
        val deleteSuccess = viewModel.deleteAdditionalWork(addedItem.id)
        assertTrue(deleteSuccess)

        val workAfterDelete = viewModel.work.value
        assertEquals(0, viewModel.getAdditionalItems(workAfterDelete).size)
        assertEquals(6, viewModel.getTotalCount(workAfterDelete))
        assertEquals(2, viewModel.getTotalCompletedCount(workAfterDelete))

        val deleteLog = workAfterDelete.activityLog.last()
        assertTrue(deleteLog.description.contains("removed"))
    }

    @Test
    fun testOverallProgressFraction() {
        val work = viewModel.work.value
        // Initial: 2 / 6 = 0.333...
        val fraction = viewModel.getProgressFraction(work)
        assertEquals(2f / 6f, fraction, 0.001f)

        // Add 2 additional items
        viewModel.addAdditionalWork("Extra item 1")
        viewModel.addAdditionalWork("Extra item 2")
        val workWithExtra = viewModel.work.value

        // Total = 8, completed = 2
        assertEquals(8, viewModel.getTotalCount(workWithExtra))
        assertEquals(2, viewModel.getTotalCompletedCount(workWithExtra))
        assertEquals(2f / 8f, viewModel.getProgressFraction(workWithExtra), 0.001f)
    }

    @Test
    fun testInitialPhotosState() {
        val work = viewModel.work.value
        assertEquals(5, viewModel.getTotalPhotosCount(work))
        assertEquals(3, viewModel.getUploadedPhotosCount(work))
        assertEquals(1, viewModel.getUploadingPhotosCount(work))
        assertEquals(1, viewModel.getFailedPhotosCount(work))
        assertEquals(0, viewModel.getPendingPhotosCount(work))
    }

    @Test
    fun testAddPhotos_increasesCountAndLogsActivity() {
        val initialCount = viewModel.getTotalPhotosCount(viewModel.work.value)
        val photo1 = viewModel.createMockPhoto("Test Inspection", PhotoCategory.SITE_INSPECTION, "Before treatment")
        val photo2 = viewModel.createMockPhoto("Chemical Spray", PhotoCategory.TREATMENT_APPLICATION)

        viewModel.addPhotos(listOf(photo1, photo2))

        val workAfterAdd = viewModel.work.value
        assertEquals(initialCount + 2, viewModel.getTotalPhotosCount(workAfterAdd))

        val added1 = workAfterAdd.photos.find { it.id == photo1.id }
        assertNotNull(added1)
        assertEquals(PhotoUploadStatus.UPLOADING, added1?.uploadStatus)
        assertEquals("Before treatment", added1?.caption)

        val latestActivity = workAfterAdd.activityLog.last()
        assertTrue(latestActivity.description.contains("2 work photos added"))
    }

    @Test
    fun testDeletePhoto_decreasesCountAndLogsActivity() {
        val initialCount = viewModel.getTotalPhotosCount(viewModel.work.value)
        val photoToDelete = viewModel.work.value.photos.first()

        val success = viewModel.deletePhoto(photoToDelete.id)
        assertTrue(success)

        val workAfterDelete = viewModel.work.value
        assertEquals(initialCount - 1, viewModel.getTotalPhotosCount(workAfterDelete))
        assertNull(workAfterDelete.photos.find { it.id == photoToDelete.id })

        val latestActivity = workAfterDelete.activityLog.last()
        assertTrue(latestActivity.description.contains("Photo removed"))
    }

    @Test
    fun testRetryPhotoUpload_updatesStatusAndLogsActivity() {
        val failedPhoto = viewModel.work.value.photos.first { it.uploadStatus == PhotoUploadStatus.FAILED }

        viewModel.retryPhotoUpload(failedPhoto.id)

        val workAfterRetry = viewModel.work.value
        val retriedPhoto = workAfterRetry.photos.first { it.id == failedPhoto.id }
        assertEquals(PhotoUploadStatus.UPLOADING, retriedPhoto.uploadStatus)

        val latestActivity = workAfterRetry.activityLog.last()
        assertTrue(latestActivity.description.contains("Photo upload retried"))
    }

    @Test
    fun testSubmitWorkForReview() {
        val initialWork = viewModel.work.value
        assertFalse(viewModel.isWaitingForReview())

        // Incomplete assigned checklist must block submission
        val submitBlocked = viewModel.submitWorkForReview()
        assertFalse("Submission must be blocked when assigned checklist is incomplete", submitBlocked)
        assertEquals("Complete all assigned tasks before submitting.", viewModel.errorMessage.value)
        assertFalse(viewModel.isWaitingForReview())

        // Complete all assigned checklist items
        viewModel.work.value.checklist.filter { !it.isCompleted }.forEach { viewModel.toggleChecklistItem(it.id) }

        val submitSuccess = viewModel.submitWorkForReview()
        assertTrue("Submission should succeed when all assigned checklist items are completed", submitSuccess)

        val workAfterSubmit = viewModel.work.value
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.WAITING_FOR_POC_REVIEW, workAfterSubmit.status)
        assertNotNull(workAfterSubmit.submittedForReviewAt)
        assertTrue(viewModel.isWaitingForReview())

        val notifications = viewModel.notifications.value
        assertTrue(notifications.any { it.title.contains("ready for review", ignoreCase = true) })
    }

    @Test
    fun testPocApproveWork() {
        viewModel.work.value.checklist.filter { !it.isCompleted }.forEach { viewModel.toggleChecklistItem(it.id) }
        viewModel.submitWorkForReview()
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.WAITING_FOR_POC_REVIEW, viewModel.work.value.status)

        val approveSuccess = viewModel.approveByPoc("Amit Kumar")
        assertTrue(approveSuccess)

        val workAfterPoc = viewModel.work.value
        assertEquals(true, workAfterPoc.pocApproved)
        assertNotNull(workAfterPoc.pocApprovalTime)
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW, workAfterPoc.status)

        val notifications = viewModel.notifications.value
        assertTrue(notifications.any { it.title.contains("POC Approved", ignoreCase = true) })
    }

    @Test
    fun testPocRejectWork() {
        viewModel.work.value.checklist.filter { !it.isCompleted }.forEach { viewModel.toggleChecklistItem(it.id) }
        viewModel.submitWorkForReview()

        val rejectSuccess = viewModel.rejectByPoc("Safety gear photo is blurry")
        assertTrue(rejectSuccess)

        val workAfterReject = viewModel.work.value
        assertEquals(false, workAfterReject.pocApproved)
        assertEquals("Safety gear photo is blurry", workAfterReject.pocRejectionReason)
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.REJECTED, workAfterReject.status)
        assertTrue(viewModel.isRejected())

        val notifications = viewModel.notifications.value
        assertTrue(notifications.any { it.title.contains("Changes Requested", ignoreCase = true) })

        // Test resume work after rejection
        viewModel.continueWorkAfterRejection()
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.IN_PROGRESS, viewModel.work.value.status)
        assertFalse(viewModel.isRejected())
    }

    @Test
    fun testSupervisorApproveAndCompleteWork() {
        viewModel.work.value.checklist.filter { !it.isCompleted }.forEach { viewModel.toggleChecklistItem(it.id) }
        viewModel.submitWorkForReview()
        viewModel.approveByPoc("Amit Kumar")

        val supervisorSuccess = viewModel.approveBySupervisor("Suresh Patel")
        assertTrue(supervisorSuccess)

        val workAfterSupervisor = viewModel.work.value
        assertEquals(true, workAfterSupervisor.supervisorApproved)
        assertNotNull(workAfterSupervisor.supervisorApprovalTime)
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.APPROVED, workAfterSupervisor.status)
        assertTrue(viewModel.isApproved())

        // Now complete work (Service boy departs site)
        val completeSuccess = viewModel.completeWork()
        assertTrue(completeSuccess)

        val completedWork = viewModel.work.value
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.COMPLETED, completedWork.status)
        assertNotNull(completedWork.completedAt)
        assertTrue(viewModel.isCompleted())
    }

    @Test
    fun testSupervisorRejectWork() {
        viewModel.submitWorkForReview()
        viewModel.approveByPoc("Amit Kumar")

        val rejectSuccess = viewModel.rejectBySupervisor("Additional work item needs re-inspection")
        assertTrue(rejectSuccess)

        val workAfterReject = viewModel.work.value
        assertEquals(false, workAfterReject.supervisorApproved)
        assertEquals("Additional work item needs re-inspection", workAfterReject.supervisorRejectionReason)
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.REJECTED, workAfterReject.status)
        assertTrue(viewModel.isRejected())

        // Test resume work after rejection
        viewModel.continueWorkAfterRejection()
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.IN_PROGRESS, viewModel.work.value.status)
        assertFalse(viewModel.isRejected())
    }

    @Test
    fun testStartWork_idempotent() {
        viewModel.startWork()
        val work1 = viewModel.work.value
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.IN_PROGRESS, work1.status)
        val initialStartTime = work1.startTime
        assertNotNull(initialStartTime)

        val startLogCount = work1.activityLog.count { it.description.contains("Work session started") }
        assertEquals(1, startLogCount)

        // Trigger startWork again
        viewModel.startWork()
        val work2 = viewModel.work.value
        assertEquals(initialStartTime, work2.startTime)
        val startLogCountAfterSecond = work2.activityLog.count { it.description.contains("Work session started") }
        assertEquals(1, startLogCountAfterSecond)
    }

    @Test
    fun testSupervisorApproval_failsWithoutPocApproval() {
        viewModel.submitWorkForReview()
        assertNull(viewModel.work.value.pocApproved)

        // Supervisor tries to approve directly without POC approval
        val supervisorSuccess = viewModel.approveBySupervisor("Suresh Patil")
        assertFalse(supervisorSuccess)
        assertNull(viewModel.work.value.supervisorApproved)
        assertFalse(viewModel.isApproved())
    }

    @Test
    fun testApproveByPoc_idempotent() {
        viewModel.submitWorkForReview()
        val firstApprove = viewModel.approveByPoc()
        assertTrue(firstApprove)
        val approvalTime = viewModel.work.value.pocApprovalTime
        assertNotNull(approvalTime)

        val pocLogCount = viewModel.work.value.activityLog.count { it.description.contains("POC approved work") }
        assertEquals(1, pocLogCount)

        // Trigger approveByPoc again
        val secondApprove = viewModel.approveByPoc()
        assertTrue(secondApprove)
        assertEquals(approvalTime, viewModel.work.value.pocApprovalTime)
        val pocLogCountAfter = viewModel.work.value.activityLog.count { it.description.contains("POC approved work") }
        assertEquals(1, pocLogCountAfter)
    }

    @Test
    fun testSupervisorApproval_idempotent() {
        viewModel.submitWorkForReview()
        viewModel.approveByPoc()
        val firstApprove = viewModel.approveBySupervisor()
        assertTrue(firstApprove)
        val supTime = viewModel.work.value.supervisorApprovalTime
        assertNotNull(supTime)

        val supLogCount = viewModel.work.value.activityLog.count { it.description.contains("Supervisor approved work") }
        assertEquals(1, supLogCount)

        // Trigger approveBySupervisor again
        val secondApprove = viewModel.approveBySupervisor()
        assertTrue(secondApprove)
        assertEquals(supTime, viewModel.work.value.supervisorApprovalTime)
        val supLogCountAfter = viewModel.work.value.activityLog.count { it.description.contains("Supervisor approved work") }
        assertEquals(1, supLogCountAfter)
    }

    @Test
    fun testCompleteWork_requiresBothApprovals() {
        viewModel.submitWorkForReview()

        // 1. Neither approved
        assertFalse(viewModel.completeWork())

        // 2. Only POC approved
        viewModel.approveByPoc()
        assertFalse(viewModel.completeWork())

        // 3. Both approved -> Can complete
        viewModel.approveBySupervisor()
        assertTrue(viewModel.completeWork())
        assertEquals(com.example.taskmanagementapplication.core.model.WorkStatus.COMPLETED, viewModel.work.value.status)
        assertTrue(viewModel.isCompleted())

        // 4. Complete work is idempotent
        assertTrue(viewModel.completeWork())
        val completionLogCount = viewModel.work.value.activityLog.count { it.description.contains("Work completed and closed") }
        assertEquals(1, completionLogCount)
    }

    @Test
    fun testApprovalStateEnumAndConvenienceProperties() {
        val initialWork = viewModel.work.value
        assertEquals(com.example.taskmanagementapplication.core.model.ApprovalState.PENDING, initialWork.pocApprovalState)
        assertEquals(com.example.taskmanagementapplication.core.model.ApprovalState.PENDING, initialWork.supervisorApprovalState)

        viewModel.submitWorkForReview()
        viewModel.approveByPoc()
        assertEquals(com.example.taskmanagementapplication.core.model.ApprovalState.APPROVED, viewModel.work.value.pocApprovalState)
        assertEquals(com.example.taskmanagementapplication.core.model.ApprovalState.PENDING, viewModel.work.value.supervisorApprovalState)

        viewModel.approveBySupervisor()
        assertEquals(com.example.taskmanagementapplication.core.model.ApprovalState.APPROVED, viewModel.work.value.supervisorApprovalState)
    }
}
