package com.example.taskmanagementapplication.work.viewmodel

import android.app.Application
import com.example.taskmanagementapplication.core.model.*
import com.example.taskmanagementapplication.core.network.AuthEventBus
import com.example.taskmanagementapplication.core.network.NetworkMonitor
import com.example.taskmanagementapplication.core.network.NetworkStatus
import com.example.taskmanagementapplication.data.dto.*
import com.example.taskmanagementapplication.data.local.CachedWorkSnapshot
import com.example.taskmanagementapplication.data.local.WorkLocalCache
import com.example.taskmanagementapplication.data.network.ApiService
import com.example.taskmanagementapplication.data.network.NetworkResult
import com.example.taskmanagementapplication.data.repository.WorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineSyncAndRecoveryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = File(System.getProperty("java.io.tmpdir") ?: ".", "sync_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private fun createFakeNetworkMonitor(online: Boolean): NetworkMonitor {
        return object : NetworkMonitor {
            override val isOnline = MutableStateFlow(online)
            override val networkStatus = MutableStateFlow(
                if (online) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
            )
        }
    }

    private fun createDummyWork(id: Long = 100L): Work {
        return Work(
            id = "W$id",
            backendId = id,
            title = "Pest Inspection",
            companyName = "ABC Corp",
            address = "Andheri, Mumbai",
            scheduledDate = "2026-09-13",
            status = WorkStatus.IN_PROGRESS,
            serviceBoyName = "Test Worker",
            pocName = "Test POC",
            supervisorName = "Test Supervisor",
            checklist = listOf(
                ChecklistItem(id = "1", title = "Inspect basement", isCompleted = false)
            )
        )
    }

    @Test
    fun testSafeOfflineRead_loadsFromSnapshotAndFormatsLastSynced() {
        val cache = WorkLocalCache(baseDir = tempDir)
        val dummyWork = createDummyWork(200L)
        val timestamp = 1700000000000L
        cache.saveSnapshot(
            CachedWorkSnapshot(
                work = dummyWork,
                additionalWork = emptyList(),
                notifications = emptyList(),
                lastSyncedAt = timestamp
            )
        )

        val offlineMonitor = createFakeNetworkMonitor(online = false)
        val fakeRepo = WorkRepository(Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService)

        val viewModel = WorkViewModel(
            application = Application(),
            repository = fakeRepo,
            networkMonitorInstance = offlineMonitor,
            cacheInstance = cache
        )

        viewModel.loadMyWork()

        assertEquals(200L, viewModel.work.value.backendId)
        assertEquals("Pest Inspection", viewModel.work.value.title)
        assertNotNull("lastSyncedText should be populated from cache", viewModel.lastSyncedText.value)
    }

    @Test
    fun testAuthoritativeWrite_blockedWhenOffline_submitForReview() {
        val offlineMonitor = createFakeNetworkMonitor(online = false)
        val fakeRepo = WorkRepository(Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService)

        val viewModel = WorkViewModel(
            application = Application(),
            repository = fakeRepo,
            networkMonitorInstance = offlineMonitor
        )

        val result = viewModel.submitWorkForReview()
        assertFalse("Submitting for review while offline must return false", result)
        assertEquals(
            "Internet connection required to complete this action",
            viewModel.errorMessage.value
        )
    }

    @Test
    fun testAuthoritativeWrite_blockedWhenOffline_approveByPoc() {
        val offlineMonitor = createFakeNetworkMonitor(online = false)
        val fakeRepo = WorkRepository(Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService)

        val viewModel = WorkViewModel(
            application = Application(),
            repository = fakeRepo,
            networkMonitorInstance = offlineMonitor
        )

        val result = viewModel.approveByPoc()
        assertFalse("POC approval while offline must return false", result)
        assertEquals(
            "Internet connection required to complete this action",
            viewModel.errorMessage.value
        )
    }

    @Test
    fun testAuthoritativeWrite_blockedWhenOffline_rejectByPoc() {
        val offlineMonitor = createFakeNetworkMonitor(online = false)
        val fakeRepo = WorkRepository(Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService)

        val viewModel = WorkViewModel(
            application = Application(),
            repository = fakeRepo,
            networkMonitorInstance = offlineMonitor
        )

        val result = viewModel.rejectByPoc("Incomplete checklist items")
        assertFalse("POC rejection while offline must return false", result)
        assertEquals(
            "Internet connection required to complete this action",
            viewModel.errorMessage.value
        )
    }

    @Test
    fun testAuthoritativeWrite_blockedWhenOffline_startWork() {
        val offlineMonitor = createFakeNetworkMonitor(online = false)
        val fakeRepo = WorkRepository(Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService)

        val viewModel = WorkViewModel(
            application = Application(),
            repository = fakeRepo,
            networkMonitorInstance = offlineMonitor
        )

        viewModel.startWork()
        assertEquals(
            "Internet connection required to complete this action",
            viewModel.errorMessage.value
        )
    }

    @Test
    fun testAuthoritativeWrite_blockedWhenOffline_completeWork() {
        val offlineMonitor = createFakeNetworkMonitor(online = false)
        val fakeRepo = WorkRepository(Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService)

        val viewModel = WorkViewModel(
            application = Application(),
            repository = fakeRepo,
            networkMonitorInstance = offlineMonitor
        )

        // Set POC & Supervisor approved
        val method = viewModel.javaClass.getDeclaredField("_work")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val workFlow = method.get(viewModel) as MutableStateFlow<Work>
        workFlow.value = workFlow.value.copy(pocApproved = true, supervisorApproved = true)

        val result = viewModel.completeWork()
        assertFalse("Completing work while offline must return false", result)
        assertEquals(
            "Internet connection required to complete this action",
            viewModel.errorMessage.value
        )
    }

    @Test
    fun testToggleChecklistItem_rollsBackOnNetworkError() = runTest(testDispatcher) {
        val onlineMonitor = createFakeNetworkMonitor(online = true)
        val fakeApiService = object : ApiService by (Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun updateChecklistItem(
                idFilter: String,
                body: ChecklistItemUpdateBody
            ): Response<List<ChecklistItemDto>> {
                return Response.error(500, "Server Error".toResponseBody("text/plain".toMediaTypeOrNull()))
            }
        }

        val repository = WorkRepository(fakeApiService, ioDispatcher = testDispatcher)
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repository,
            networkMonitorInstance = onlineMonitor
        )

        // Set a checklist item
        val method = viewModel.javaClass.getDeclaredField("_work")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val workFlow = method.get(viewModel) as MutableStateFlow<Work>
        val initialItem = ChecklistItem(id = "1", title = "Check pipes", isCompleted = false)
        workFlow.value = workFlow.value.copy(backendId = 100L, checklist = listOf(initialItem))

        viewModel.toggleChecklistItem("1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Check that item is rolled back to isCompleted = false
        val rolledBackItem = viewModel.work.value.checklist.first { it.id == "1" }
        assertFalse("Item should be rolled back to incomplete after network failure", rolledBackItem.isCompleted)
        assertNotNull("Error message should be displayed on rollback", viewModel.errorMessage.value)
    }

    @Test
    fun testAuthoritativeWrite_blockedWhenOffline_addAdditionalWork() {
        val offlineMonitor = createFakeNetworkMonitor(online = false)
        val fakeRepo = WorkRepository(Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService)

        val viewModel = WorkViewModel(
            application = Application(),
            repository = fakeRepo,
            networkMonitorInstance = offlineMonitor
        )

        val result = viewModel.addAdditionalWork("Fix broken pipe")
        assertFalse("Adding additional work while offline must return false", result)
        assertEquals(
            "Internet connection required to complete this action",
            viewModel.errorMessage.value
        )
    }

    @Test
    fun testAuthEventBus_sessionExpired_emitsEvent() = runTest {
        var received = false
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            AuthEventBus.sessionExpired.collect {
                received = true
            }
        }

        AuthEventBus.emitSessionExpired()

        assertTrue("AuthEventBus must emit SessionExpired", received)
        job.cancel()
    }

    @Test
    fun testWorkRepository_uploadPhoto_passesClientPhotoId() = runTest {
        var capturedClientId: String? = null

        val fakeApiService = object : ApiService by (Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun uploadPhotoToStorage(path: String, photoBytes: RequestBody): Response<ResponseBody> {
                return Response.success("".toResponseBody("application/json".toMediaTypeOrNull()))
            }
            override suspend fun createPhotoRecord(record: CreatePhotoRecordBody): Response<List<WorkPhotoDto>> {
                capturedClientId = record.clientPhotoId
                return Response.success(
                    listOf(
                        WorkPhotoDto(
                            id = 77L,
                            workId = record.workId,
                            title = record.title,
                            category = record.category,
                            clientPhotoId = record.clientPhotoId,
                            uploadStatus = "UPLOADED"
                        )
                    )
                )
            }
        }

        val repository = WorkRepository(fakeApiService)
        val dummyPart = MultipartBody.Part.createFormData(
            "file", "pipe.jpg", "bytes".toRequestBody("image/jpeg".toMediaTypeOrNull())
        )

        val result = repository.uploadPhoto(
            workId = 50L,
            filePart = dummyPart,
            titlePart = "Pipe".toRequestBody("text/plain".toMediaTypeOrNull()),
            categoryPart = "EQUIPMENT".toRequestBody("text/plain".toMediaTypeOrNull()),
            clientPhotoIdPart = "client-uuid-999".toRequestBody("text/plain".toMediaTypeOrNull())
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals("client-uuid-999", capturedClientId)
    }

    @Test
    fun testWorkRepository_createAdditionalWork_passesClientItemId() = runTest {
        var capturedClientItemId: String? = null

        val fakeApiService = object : ApiService by (Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun createAdditionalWork(
                body: CreateAdditionalWorkBody
            ): Response<List<AdditionalWorkDto>> {
                capturedClientItemId = body.clientItemId
                return Response.success(
                    listOf(
                        AdditionalWorkDto(
                            id = 88L,
                            workId = body.workId,
                            description = body.description,
                            clientItemId = body.clientItemId
                        )
                    )
                )
            }
        }

        val repository = WorkRepository(fakeApiService)
        val result = repository.createAdditionalWork(
            workId = 50L,
            description = "Extra welding job",
            clientItemId = "client-item-uuid-777"
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals("client-item-uuid-777", capturedClientItemId)
    }
}
