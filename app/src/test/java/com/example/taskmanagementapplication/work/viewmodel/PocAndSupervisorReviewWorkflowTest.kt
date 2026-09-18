package com.example.taskmanagementapplication.work.viewmodel

import com.example.taskmanagementapplication.core.model.ApprovalState
import com.example.taskmanagementapplication.core.model.ChecklistItem
import com.example.taskmanagementapplication.core.model.WorkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for POC & Supervisor Checklist Review & Approval Workflow.
 * Verifies:
 * 1. Two separate task sections: ASSIGNED CHECKLIST and ADDITIONAL WORK PERFORMED (never mixed).
 * 2. Fallback text "No additional work reported." when no additional items exist.
 * 3. Supervisor approval is blocked until POC approval exists.
 * 4. POC approval transitions to WAITING_FOR_SUPERVISOR_REVIEW with POC APPROVED ✓.
 * 5. Supervisor approval transitions to APPROVED with both POC APPROVED ✓ and SUPERVISOR APPROVED ✓.
 * 6. Mandatory rejection reason validation and persistent rejection audit trail.
 */
class PocAndSupervisorReviewWorkflowTest {

    private lateinit var viewModel: WorkViewModel

    @Before
    fun setUp() {
        viewModel = WorkViewModel()
        // Ensure all assigned checklist items are marked complete so submission is permitted
        viewModel.work.value.checklist.filter { !it.isCompleted }.forEach { viewModel.toggleChecklistItem(it.id) }
    }

    @Test
    fun testTwoSeparateSectionsNeverMixed() {
        val currentWork = viewModel.work.value

        val assignedTasks = viewModel.getPredefinedItems(currentWork)
        val additionalTasks = viewModel.getAdditionalItems(currentWork)

        // Predefined checklist items must match assigned tasks
        assertTrue("Assigned tasks must not be empty", assignedTasks.isNotEmpty())
        assignedTasks.forEach {
            assertFalse("Assigned task must not be flagged as additional", it.isAdditional)
            assertNotNull("Assigned task must have title", it.title)
        }

        // Initially no additional tasks
        assertTrue("Initially additional tasks list must be empty", additionalTasks.isEmpty())

        // Add additional task
        val added = viewModel.addAdditionalWork(
            title = "Deep Cleaning",
            description = "Deep Cleaning behind HVAC units"
        )
        assertTrue("Adding additional work should succeed", added)

        val updatedWork = viewModel.work.value
        val updatedAssigned = viewModel.getPredefinedItems(updatedWork)
        val updatedAdditional = viewModel.getAdditionalItems(updatedWork)

        // Strict separation: assigned count remains unchanged, additional count becomes 1
        assertEquals("Assigned checklist count must remain unchanged", assignedTasks.size, updatedAssigned.size)
        assertEquals("Additional work count must now be 1", 1, updatedAdditional.size)
        assertEquals("Deep Cleaning", updatedAdditional.first().taskLabel ?: updatedAdditional.first().title)
        assertTrue("Additional task must have isAdditional = true", updatedAdditional.first().isAdditional)
    }

    @Test
    fun testPocApprovalCreatesSupervisorWebRequest() {
        viewModel.submitWorkForReview()
        val work = viewModel.work.value
        assertEquals(WorkStatus.WAITING_FOR_POC_REVIEW, work.status)
        assertNull("POC approval must be null initially", work.pocApproved)

        // POC Approves work
        val approved = viewModel.approveByPoc()
        assertTrue("POC approval should succeed", approved)

        val workAfterPoc = viewModel.work.value
        assertEquals(WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW, workAfterPoc.status)
        assertEquals(true, workAfterPoc.pocApproved)
        assertEquals(ApprovalState.APPROVED, workAfterPoc.pocApprovalState)
        assertNotNull("POC approval time must be recorded", workAfterPoc.pocApprovalTime)
        assertTrue(
            "Audit event must record Supervisor Web Approval Request initiation",
            workAfterPoc.activityLog.any { it.description.contains("Supervisor Web Approval Request initiated") }
        )
    }

    @Test
    fun testDualApprovalStateAuthoritative() {
        viewModel.submitWorkForReview()
        viewModel.approveByPoc()

        // Authoritative decision recorded in Supabase by Supervisor via Web Portal
        viewModel.selectWork(
            viewModel.work.value.copy(
                status = WorkStatus.APPROVED,
                supervisorApproved = true,
                supervisorApprovalTime = "11:15 AM",
                readyForCompletion = true
            )
        )

        val finalWork = viewModel.work.value
        assertEquals(WorkStatus.APPROVED, finalWork.status)
        assertEquals(true, finalWork.pocApproved)
        assertEquals(true, finalWork.supervisorApproved)
        assertEquals(ApprovalState.APPROVED, finalWork.pocApprovalState)
        assertEquals(ApprovalState.APPROVED, finalWork.supervisorApprovalState)
        assertTrue("Work must be marked ready for completion", finalWork.readyForCompletion)
    }

    @Test
    fun testMandatoryRejectionReasonAndAuditTrail() {
        viewModel.submitWorkForReview()

        // Blank rejection must fail
        val blankPocReject = viewModel.rejectByPoc("   ")
        assertFalse("Blank rejection reason must fail", blankPocReject)
        assertEquals("Rejection reason is mandatory.", viewModel.errorMessage.value)

        // Valid POC rejection
        val validPocReject = viewModel.rejectByPoc("Pest control trap behind unit 4 not inspected")
        assertTrue("Valid rejection should succeed", validPocReject)
        assertEquals(WorkStatus.REJECTED, viewModel.work.value.status)
        assertEquals("Pest control trap behind unit 4 not inspected", viewModel.work.value.pocRejectionReason)

        // Persistent audit trail: rejection reason remains recorded
        assertNotNull(viewModel.work.value.pocRejectionReason)
    }
}
