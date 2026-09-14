package com.example.taskmanagementapplication.work.viewmodel

import android.app.Application
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.data.dto.WorkReportDto
import com.example.taskmanagementapplication.data.network.ApiService
import com.example.taskmanagementapplication.data.repository.WorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
class WorkReportTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = File(System.getProperty("java.io.tmpdir") ?: ".", "report_test_${System.currentTimeMillis()}").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private fun createFakeApiService(
        getReportHandler: suspend (String) -> Response<List<WorkReportDto>> = { Response.error(404, "".toResponseBody(null)) },
        downloadHandler: suspend (String) -> Response<ResponseBody> = { Response.error(404, "".toResponseBody(null)) }
    ): ApiService {
        val dummyProxy = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService

        return object : ApiService by dummyProxy {
            override suspend fun getWorkReport(workIdFilter: String): Response<List<WorkReportDto>> {
                return getReportHandler(workIdFilter)
            }
            override suspend fun downloadReportFromStorage(path: String): Response<ResponseBody> {
                return downloadHandler(path)
            }
        }
    }

    @Test
    fun testInitialReportStateIsIdle() {
        val fakeApi = createFakeApiService()
        val repo = WorkRepository(fakeApi, testDispatcher)
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            ioDispatcher = testDispatcher
        )

        assertEquals(ReportUiState.Idle, viewModel.reportState.value)
        assertFalse(viewModel.isDownloadingReport.value)
    }

    @Test
    fun testLoadWorkReportSuccessUpdatesState() = runTest(testDispatcher) {
        val fakeDto = WorkReportDto(
            id = 1L,
            workId = 100L,
            reportNumber = "REP-W100-12345",
            fileName = "WorkReport_100.pdf",
            contentType = "application/pdf",
            fileSize = 4096L,
            generatedAt = "2026-09-13T07:30:00Z",
            version = 1,
            downloadUrl = "/api/works/100/report/download",
            duration = "1h 45m 20s",
            workTitle = "HVAC Maintenance",
            clientName = "Technopark Inc",
            completedAt = "2026-09-13T07:30:00Z"
        )

        val fakeApi = createFakeApiService(
            getReportHandler = { Response.success(listOf(fakeDto)) }
        )
        val repo = WorkRepository(fakeApi, testDispatcher)
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            ioDispatcher = testDispatcher
        )

        viewModel.loadWorkReport(100L)
        advanceUntilIdle()

        val state = viewModel.reportState.value
        assertTrue("State should be Success", state is ReportUiState.Success)
        val success = state as ReportUiState.Success
        assertEquals("REP-W100-12345", success.report.reportNumber)
        assertEquals("WorkReport_100.pdf", success.report.fileName)
        assertEquals("1h 45m 20s", success.report.duration)
        assertEquals(4096L, success.report.fileSize)
    }

    @Test
    fun testLoadWorkReportErrorUpdatesState() = runTest(testDispatcher) {
        val errorBody = "{\"message\":\"Work not completed\"}".toResponseBody("application/json".toMediaTypeOrNull())
        val fakeApi = createFakeApiService(
            getReportHandler = { Response.error(400, errorBody) }
        )
        val repo = WorkRepository(fakeApi, testDispatcher)
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            ioDispatcher = testDispatcher
        )

        viewModel.loadWorkReport(100L)
        advanceUntilIdle()

        val state = viewModel.reportState.value
        assertTrue("State should be Error", state is ReportUiState.Error)
    }

    @Test
    fun testDownloadAndSaveReportSavesPdfFile() = runTest(testDispatcher) {
        val pdfContent = "%PDF-1.4 Mock Authoritative Report Content"

        val fakeApi = createFakeApiService(
            downloadHandler = {
                Response.success(pdfContent.toResponseBody("application/pdf".toMediaTypeOrNull()))
            }
        )
        val repo = WorkRepository(fakeApi, testDispatcher)
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            ioDispatcher = testDispatcher
        )

        var downloadedFile: File? = null
        var downloadError: String? = null

        viewModel.downloadAndSaveReport(
            workId = 100L,
            onSuccess = { file -> downloadedFile = file },
            onError = { err -> downloadError = err }
        )
        advanceUntilIdle()

        assertNull("Error should be null", downloadError)
        assertNotNull("Downloaded file should not be null", downloadedFile)
        assertTrue("File should exist", downloadedFile!!.exists())
        assertEquals("WorkReport_100.pdf", downloadedFile!!.name)
        assertEquals(pdfContent, downloadedFile!!.readText())
    }

    @Test
    fun testCanCompleteWorkLogic() {
        val fakeApi = createFakeApiService()
        val repo = WorkRepository(fakeApi, testDispatcher)
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repo,
            ioDispatcher = testDispatcher
        )

        // Initially demo work is not approved
        var currentWork = viewModel.work.value
        assertFalse(viewModel.canCompleteWork(currentWork))

        // After both approved
        currentWork = currentWork.copy(
            pocApproved = true,
            supervisorApproved = true,
            status = WorkStatus.APPROVED
        )
        assertTrue(viewModel.canCompleteWork(currentWork))

        // Once already completed, canCompleteWork should be false
        val completedWork = currentWork.copy(status = WorkStatus.COMPLETED)
        assertFalse(viewModel.canCompleteWork(completedWork))
    }
}
