package com.example.taskmanagementapplication.work.viewmodel

import com.example.taskmanagementapplication.core.model.ApprovalState
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.data.dto.*
import com.example.taskmanagementapplication.data.network.ApiService
import com.example.taskmanagementapplication.data.network.NetworkResult
import com.example.taskmanagementapplication.data.repository.WorkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalWorkflowTest {

    private lateinit var viewModel: WorkViewModel

    @Before
    fun setUp() {
        viewModel = WorkViewModel()
        viewModel.work.value.checklist.filter { !it.isCompleted }.forEach { viewModel.toggleChecklistItem(it.id) }
    }

    @Test
    fun testPocRejectRequiresNonEmptyReason() {
        viewModel.submitWorkForReview()
        assertEquals(WorkStatus.WAITING_FOR_POC_REVIEW, viewModel.work.value.status)

        // Try blank reason
        val emptyReject = viewModel.rejectByPoc("   ")
        assertFalse("Rejection with blank reason must be rejected", emptyReject)
        assertEquals("Rejection reason is mandatory.", viewModel.errorMessage.value)
        assertEquals(WorkStatus.WAITING_FOR_POC_REVIEW, viewModel.work.value.status)

        // Try valid reason
        val validReject = viewModel.rejectByPoc("Photo 2 does not clearly show the valve")
        assertTrue(validReject)
        assertEquals(WorkStatus.REJECTED, viewModel.work.value.status)
        assertEquals("Photo 2 does not clearly show the valve", viewModel.work.value.pocRejectionReason)
        assertEquals(false, viewModel.work.value.pocApproved)
    }

    @Test
    fun testSupervisorRejectRequiresNonEmptyReason() {
        viewModel.submitWorkForReview()
        viewModel.approveByPoc()
        assertEquals(WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW, viewModel.work.value.status)

        // Try blank reason
        val emptyReject = viewModel.rejectBySupervisor("")
        assertFalse("Supervisor rejection with blank reason must be rejected", emptyReject)
        assertEquals("Rejection reason is mandatory.", viewModel.errorMessage.value)

        // Try valid reason
        val validReject = viewModel.rejectBySupervisor("Missing site clearing photo")
        assertTrue(validReject)
        assertEquals(WorkStatus.REJECTED, viewModel.work.value.status)
        assertEquals("Missing site clearing photo", viewModel.work.value.supervisorRejectionReason)
    }

    @Test
    fun testSupervisorCannotApproveBeforePoc() {
        viewModel.submitWorkForReview()
        assertNull(viewModel.work.value.pocApproved)

        val result = viewModel.approveBySupervisor()
        assertFalse("Supervisor must not be able to approve before POC", result)
        assertEquals(
            "Supervisor approval is unavailable until POC approval is completed.",
            viewModel.errorMessage.value
        )
        assertNull(viewModel.work.value.supervisorApproved)
    }

    @Test
    fun testCompleteApprovalCycleToReadyForCompletion() {
        viewModel.submitWorkForReview()
        assertEquals(WorkStatus.WAITING_FOR_POC_REVIEW, viewModel.work.value.status)

        // POC Approves
        val pocResult = viewModel.approveByPoc()
        assertTrue(pocResult)
        assertEquals(WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW, viewModel.work.value.status)
        assertEquals(true, viewModel.work.value.pocApproved)
        assertEquals(ApprovalState.APPROVED, viewModel.work.value.pocApprovalState)

        // Supervisor Approves
        val supResult = viewModel.approveBySupervisor()
        assertTrue(supResult)
        assertEquals(WorkStatus.APPROVED, viewModel.work.value.status)
        assertEquals(true, viewModel.work.value.supervisorApproved)
        assertEquals(ApprovalState.APPROVED, viewModel.work.value.supervisorApprovalState)
        assertTrue("Work should be ready for completion", viewModel.work.value.readyForCompletion)

        // Service Boy Completes
        val completeResult = viewModel.completeWork()
        assertTrue(completeResult)
        assertEquals(WorkStatus.COMPLETED, viewModel.work.value.status)
    }

    @Test
    fun testReworkLifecycleAfterRejection() {
        viewModel.submitWorkForReview()
        viewModel.rejectByPoc("Incomplete checklist task")
        assertEquals(WorkStatus.REJECTED, viewModel.work.value.status)

        // Continue work
        viewModel.continueWorkAfterRejection()
        assertEquals(WorkStatus.IN_PROGRESS, viewModel.work.value.status)
        assertFalse(viewModel.isRejected())

        // Re-submit for review
        viewModel.submitWorkForReview()
        assertEquals(WorkStatus.WAITING_FOR_POC_REVIEW, viewModel.work.value.status)
        assertNull(viewModel.work.value.pocApproved)
        assertNull(viewModel.work.value.supervisorApproved)
    }

    @Test
    fun testWorkRepository_pocDecision_success() = runTest {
        val fakeApiService = object : ApiService by (java.lang.reflect.Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun pocDecision(
                request: PocDecisionRpcRequest
            ): Response<ApprovalDto> {
                return Response.success(
                    ApprovalDto(
                        id = 1L,
                        workId = request.workId,
                        approverRole = "POC",
                        status = request.decision,
                        rejectionReason = request.reason,
                        decidedAt = "2026-09-13T06:30:00Z"
                    )
                )
            }
        }

        val repo = WorkRepository(fakeApiService)
        val result = repo.pocApprove(10L)
        assertTrue(result is NetworkResult.Success)
        val approval = (result as NetworkResult.Success).data
        assertEquals("APPROVED", approval.status)
        assertEquals("POC", approval.approverRole)
        assertEquals("2026-09-13T06:30:00Z", approval.decidedAt)
    }

    @Test
    fun testWorkRepository_supervisorDecision_reject_success() = runTest {
        val fakeApiService = object : ApiService by (java.lang.reflect.Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun supervisorDecision(
                request: SupervisorDecisionRpcRequest
            ): Response<ApprovalDto> {
                return Response.success(
                    ApprovalDto(
                        id = 2L,
                        workId = request.workId,
                        approverRole = "SUPERVISOR",
                        status = request.decision,
                        rejectionReason = request.reason,
                        decidedAt = "2026-09-13T06:45:00Z"
                    )
                )
            }
        }

        val repo = WorkRepository(fakeApiService)
        val result = repo.supervisorReject(10L, "Fix valve leak")
        assertTrue(result is NetworkResult.Success)
        val approval = (result as NetworkResult.Success).data
        assertEquals("REJECTED", approval.status)
        assertEquals("Fix valve leak", approval.rejectionReason)
    }

    @Test
    fun testWorkRepository_getNotifications_mapping() = runTest {
        val fakeApiService = object : ApiService by (java.lang.reflect.Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun getNotifications(order: String): Response<List<NotificationDto>> {
                return Response.success(
                    listOf(
                        NotificationDto(
                            id = 501L,
                            userId = 2L,
                            workId = 10L,
                            title = "Work Approved by POC",
                            message = "POC approved work. Ready for supervisor review.",
                            type = "POC_APPROVED",
                            isRead = false,
                            deliveryStatus = "SENT",
                            createdAt = "2026-09-13T06:30:00Z"
                        )
                    )
                )
            }
        }

        val repo = WorkRepository(fakeApiService)
        val result = repo.getNotifications()
        assertTrue(result is NetworkResult.Success)
        val notifications = (result as NetworkResult.Success).data
        assertEquals(1, notifications.size)
        val n = notifications.first()
        assertEquals("501", n.id)
        assertEquals("10", n.relatedWorkId)
        assertEquals("Work Approved by POC", n.title)
        assertEquals(false, n.isRead)
        assertNotNull(n.timestamp)
        assertTrue(n.timestamp.isNotBlank())
    }
}
