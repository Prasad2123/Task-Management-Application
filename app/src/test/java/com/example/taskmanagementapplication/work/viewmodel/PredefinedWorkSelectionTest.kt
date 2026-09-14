package com.example.taskmanagementapplication.work.viewmodel

import android.app.Application
import com.example.taskmanagementapplication.core.model.Work
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.core.network.NetworkMonitor
import com.example.taskmanagementapplication.core.network.NetworkStatus
import com.example.taskmanagementapplication.data.dto.*
import com.example.taskmanagementapplication.data.network.ApiService
import com.example.taskmanagementapplication.data.repository.WorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class PredefinedWorkSelectionTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createOnlineMonitor(): NetworkMonitor {
        return object : NetworkMonitor {
            override val isOnline = MutableStateFlow(true)
            override val networkStatus = MutableStateFlow(NetworkStatus.ONLINE)
        }
    }

    private fun createWorkDto(
        id: Long,
        serviceBoyId: Long,
        title: String = "Inspection #$id",
        status: String = "NOT_STARTED",
        scheduledDate: String = "2026-09-14",
        companyName: String = "Client $id",
        address: String = "Site $id"
    ): WorkDto {
        return WorkDto(
            id = id,
            title = title,
            status = status,
            serviceBoyId = serviceBoyId,
            scheduledDate = scheduledDate,
            companyName = companyName,
            address = address
        )
    }

    private fun createRepositoryWithWorks(worksSupplier: () -> List<WorkDto>): WorkRepository {
        val dummyProxy = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService

        val fakeApiService = object : ApiService by dummyProxy {
            override suspend fun getMyWorks(): Response<List<WorkDto>> =
                Response.success(worksSupplier())

            override suspend fun getChecklist(workId: String, order: String): Response<List<ChecklistItemDto>> =
                Response.success(emptyList())

            override suspend fun getAdditionalWork(workId: String, order: String): Response<List<AdditionalWorkDto>> =
                Response.success(emptyList())

            override suspend fun getApprovals(workId: String): Response<List<ApprovalDto>> =
                Response.success(emptyList())

            override suspend fun getActivity(workId: String, order: String): Response<List<ActivityEventDto>> =
                Response.success(emptyList())

            override suspend fun getPhotos(workId: String, order: String): Response<List<WorkPhotoDto>> =
                Response.success(emptyList())

            override suspend fun getNotifications(order: String): Response<List<NotificationDto>> =
                Response.success(emptyList())
        }
        return WorkRepository(fakeApiService, null, testDispatcher)
    }

    /**
     * Requirement 4: Never select another Service Boy's work.
     * Supabase RLS and get_my_works() strictly isolate works by authenticated service_boy_id.
     */
    @Test
    fun testServiceBoyCannotSeeOtherServiceBoysPrivateWork() = runTest(testDispatcher) {
        val serviceBoyAId = 101L
        val serviceBoyBId = 202L

        // Backend database contains works for both Service Boy A and Service Boy B
        val allDatabaseWorks = listOf(
            createWorkDto(id = 10L, serviceBoyId = serviceBoyAId, title = "Boy A Private Work"),
            createWorkDto(id = 20L, serviceBoyId = serviceBoyBId, title = "Boy B Private Work")
        )

        // Supabase get_my_works() RPC executes:
        // SELECT * FROM public.works WHERE service_boy_id = v_caller_id
        val serviceBoyARepo = createRepositoryWithWorks {
            allDatabaseWorks.filter { it.serviceBoyId == serviceBoyAId }
        }

        val viewModelA = WorkViewModel(
            application = Application(),
            repository = serviceBoyARepo,
            networkMonitorInstance = createOnlineMonitor(),
            initialWork = null
        )

        viewModelA.loadMyWork()
        testDispatcher.scheduler.advanceUntilIdle()

        // Service Boy A must only see their own predefined work (id = 10L)
        assertEquals(1, viewModelA.predefinedWorks.value.size)
        assertEquals(10L, viewModelA.predefinedWorks.value.first().backendId)
        assertEquals("Boy A Private Work", viewModelA.predefinedWorks.value.first().title)
        assertEquals(10L, viewModelA.work.value.backendId)

        // Verify Service Boy B's work is NEVER present in Service Boy A's state
        assertFalse(
            "Service Boy A must never see Service Boy B's work",
            viewModelA.predefinedWorks.value.any { it.backendId == 20L }
        )
    }

    /**
     * Requirement 5: If one active/predefined work exists, show it.
     */
    @Test
    fun testSinglePredefinedWork_automaticallyShown() = runTest(testDispatcher) {
        val serviceBoyId = 300L
        val singleWork = listOf(
            createWorkDto(id = 555L, serviceBoyId = serviceBoyId, title = "Sole Active Work", status = "NOT_STARTED")
        )

        val repo = createRepositoryWithWorks { singleWork }
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            networkMonitorInstance = createOnlineMonitor(),
            initialWork = null
        )

        viewModel.loadMyWork()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.predefinedWorks.value.size)
        assertEquals(555L, viewModel.work.value.backendId)
        assertEquals("Sole Active Work", viewModel.work.value.title)
        assertTrue(viewModel.hasActiveWork)
    }

    /**
     * Requirement 6: If multiple predefined/active works exist, show the list
     * and let the Service Boy select the appropriate work (no arbitrary fallback).
     */
    @Test
    fun testMultiplePredefinedWorks_requiresServiceBoySelection_andSelectWorkWorks() = runTest(testDispatcher) {
        val serviceBoyId = 300L
        val multipleWorks = listOf(
            createWorkDto(id = 701L, serviceBoyId = serviceBoyId, title = "Building A Pest Control", status = "NOT_STARTED"),
            createWorkDto(id = 702L, serviceBoyId = serviceBoyId, title = "Building B Inspection", status = "NOT_STARTED"),
            createWorkDto(id = 703L, serviceBoyId = serviceBoyId, title = "Building C Preventive Maintenance", status = "NOT_STARTED")
        )

        val repo = createRepositoryWithWorks { multipleWorks }
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            networkMonitorInstance = createOnlineMonitor(),
            initialWork = null
        )

        viewModel.loadMyWork()
        testDispatcher.scheduler.advanceUntilIdle()

        // Multiple predefined works must all be present in predefinedWorks
        assertEquals(3, viewModel.predefinedWorks.value.size)

        // Neither the first work nor an arbitrary work should be forced as active
        assertFalse(
            "When multiple active works exist and none is IN_PROGRESS, no arbitrary work should be auto-selected",
            viewModel.hasActiveWork
        )
        assertNull(viewModel.work.value.backendId)

        // Service Boy selects Building B (id = 702L)
        val selectedWork = viewModel.predefinedWorks.value.first { it.backendId == 702L }
        viewModel.selectWork(selectedWork)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(702L, viewModel.work.value.backendId)
        assertEquals("Building B Inspection", viewModel.work.value.title)
        assertTrue(viewModel.hasActiveWork)
    }

    /**
     * Requirement 1 & 10: No hardcoded work_id = 1 or 2, and dynamic arbitrary IDs are handled seamlessly.
     */
    @Test
    fun testNoHardcodedWorkIdIsUsed() = runTest(testDispatcher) {
        val serviceBoyId = 999L
        // Dynamic non-standard IDs: 987654L
        val dynamicWork = listOf(
            createWorkDto(id = 987654L, serviceBoyId = serviceBoyId, title = "Dynamic Arbitrary ID Work")
        )

        val repo = createRepositoryWithWorks { dynamicWork }
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            networkMonitorInstance = createOnlineMonitor(),
            initialWork = null
        )

        viewModel.loadMyWork()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(987654L, viewModel.work.value.backendId)
        assertEquals("Dynamic Arbitrary ID Work", viewModel.work.value.title)
    }

    /**
     * Requirement 7: IN_PROGRESS work takes priority over merely pending work
     * even if pending work has earlier date or appears earlier in query.
     */
    @Test
    fun testInProgressWorkTakesPriorityOverPendingWork() = runTest(testDispatcher) {
        val serviceBoyId = 300L
        val works = listOf(
            // First item with earlier date is pending
            createWorkDto(
                id = 801L,
                serviceBoyId = serviceBoyId,
                title = "Pending Early Work",
                status = "NOT_STARTED",
                scheduledDate = "2026-09-01"
            ),
            // Second item with later date is IN_PROGRESS
            createWorkDto(
                id = 802L,
                serviceBoyId = serviceBoyId,
                title = "In-Progress Ongoing Work",
                status = "IN_PROGRESS",
                scheduledDate = "2026-09-30"
            )
        )

        val repo = createRepositoryWithWorks { works }
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            networkMonitorInstance = createOnlineMonitor(),
            initialWork = null
        )

        viewModel.loadMyWork()
        testDispatcher.scheduler.advanceUntilIdle()

        // Must select the IN_PROGRESS work (id = 802L), NOT the earliest/first result
        assertEquals(802L, viewModel.work.value.backendId)
        assertEquals(WorkStatus.IN_PROGRESS, viewModel.work.value.status)
        assertEquals("In-Progress Ongoing Work", viewModel.work.value.title)
    }

    /**
     * Requirement 9: Completed work must NOT incorrectly become current active work.
     */
    @Test
    fun testCompletedWorkIsNotSelectedAsCurrentActiveWork() = runTest(testDispatcher) {
        val serviceBoyId = 300L
        val completedWorks = listOf(
            createWorkDto(id = 901L, serviceBoyId = serviceBoyId, title = "Completed Job", status = "COMPLETED")
        )

        val repo = createRepositoryWithWorks { completedWorks }
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            networkMonitorInstance = createOnlineMonitor(),
            initialWork = null
        )

        viewModel.loadMyWork()
        testDispatcher.scheduler.advanceUntilIdle()

        // Predefined list contains the completed work
        assertEquals(1, viewModel.predefinedWorks.value.size)
        assertEquals(901L, viewModel.predefinedWorks.value.first().backendId)

        // But it MUST NOT be selected as current active work!
        assertFalse("Completed work must not be active", viewModel.hasActiveWork)
        assertNull("Current active work backendId must be null when all works completed", viewModel.work.value.backendId)
    }

    /**
     * Requirement 8: Rejected / rework work must remain accessible to the correct Service Boy.
     */
    @Test
    fun testRejectedWorkRemainsAccessibleForRework() = runTest(testDispatcher) {
        val serviceBoyId = 300L
        val reworkWork = listOf(
            createWorkDto(id = 950L, serviceBoyId = serviceBoyId, title = "Fix Valve Leak", status = "REJECTED")
        )

        val repo = createRepositoryWithWorks { reworkWork }
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            networkMonitorInstance = createOnlineMonitor(),
            initialWork = null
        )

        viewModel.loadMyWork()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(950L, viewModel.work.value.backendId)
        assertEquals(WorkStatus.REJECTED, viewModel.work.value.status)
        assertTrue("Rejected/rework work is considered active and actionable", viewModel.hasActiveWork)
    }
}
