package com.example.taskmanagementapplication.work.viewmodel

import android.app.Application
import com.example.taskmanagementapplication.core.location.LocationClient
import com.example.taskmanagementapplication.core.location.LocationResult
import com.example.taskmanagementapplication.core.model.Work
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.core.util.GeoUtils
import com.example.taskmanagementapplication.data.dto.*
import com.example.taskmanagementapplication.data.network.ApiService
import com.example.taskmanagementapplication.data.network.NetworkResult
import com.example.taskmanagementapplication.data.repository.WorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class WorkLocationTest {

    private val testDispatcher = StandardTestDispatcher()

    // Site coordinates: Andheri East, Mumbai
    private val siteLat = 19.1136
    private val siteLng = 72.8697
    private val allowedRadius = 150.0

    class FakeLocationClient(var result: LocationResult = LocationResult.GpsDisabled) : LocationClient {
        override suspend fun getCurrentLocation(): LocationResult = result
    }

    private fun createFakeApiService(
        onStartWork: (StartWorkRpcRequest) -> Response<WorkDto>
    ): ApiService {
        return object : ApiService by (Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun startWork(request: StartWorkRpcRequest): Response<WorkDto> {
                return onStartWork(request)
            }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 1. GeoUtils Distance Calculations ──

    @Test
    fun testGeoUtils_distanceCalculation() {
        // Same point distance should be 0
        val distZero = GeoUtils.calculateDistanceMeters(siteLat, siteLng, siteLat, siteLng)
        assertEquals(0.0, distZero, 0.001)

        // Nearby point (~50m away)
        val distNear = GeoUtils.calculateDistanceMeters(siteLat, siteLng, 19.1139, 72.8699)
        assertTrue(distNear in 30.0..70.0)

        // Far point (~5km away)
        val distFar = GeoUtils.calculateDistanceMeters(siteLat, siteLng, 19.0760, 72.8777)
        assertTrue(distFar > 3000.0)
    }

    @Test
    fun testGeoUtils_coordinateValidation() {
        assertTrue(GeoUtils.isValidCoordinate(19.1136, 72.8697))
        assertTrue(GeoUtils.isValidCoordinate(0.0, 0.0))
        assertTrue(GeoUtils.isValidCoordinate(-90.0, 180.0))

        assertFalse(GeoUtils.isValidCoordinate(null, 72.8697))
        assertFalse(GeoUtils.isValidCoordinate(19.1136, null))
        assertFalse(GeoUtils.isValidCoordinate(91.0, 72.8697))
        assertFalse(GeoUtils.isValidCoordinate(-91.0, 72.8697))
        assertFalse(GeoUtils.isValidCoordinate(19.1136, 181.0))
        assertFalse(GeoUtils.isValidCoordinate(19.1136, -181.0))
    }

    @Test
    fun testGeoUtils_distanceFormatting() {
        assertEquals("82 m", GeoUtils.formatDistance(82.4))
        assertEquals("150 m", GeoUtils.formatDistance(150.0))
        assertEquals("1.2 km", GeoUtils.formatDistance(1200.0))
    }

    // ── 2. Location Verification States ──

    @Test
    fun testLocationRefresh_whenInsideRadius_setsVerified() = runTest(testDispatcher) {
        val fakeLocationClient = FakeLocationClient(
            LocationResult.Success(
                latitude = 19.1138,
                longitude = 72.8698,
                accuracyMeters = 10.0f,
                timestamp = System.currentTimeMillis()
            )
        )

        val viewModel = WorkViewModel(
            application = Application(),
            locationClientInstance = fakeLocationClient
        )

        viewModel.refreshLocation()
        advanceUntilIdle()

        val state = viewModel.locationState.value
        assertEquals(LocationVerificationStatus.VERIFIED, state.status)
        assertTrue(state.isInsideRadius)
        assertNotNull(state.distanceFromWorkMeters)
        assertTrue(state.distanceFromWorkMeters!! <= allowedRadius)
        assertNull(state.errorMessage)
    }

    @Test
    fun testLocationRefresh_whenOutsideRadius_setsTooFar() = runTest(testDispatcher) {
        val fakeLocationClient = FakeLocationClient(
            LocationResult.Success(
                latitude = 19.1180,
                longitude = 72.8720,
                accuracyMeters = 15.0f,
                timestamp = System.currentTimeMillis()
            )
        )

        val viewModel = WorkViewModel(
            application = Application(),
            locationClientInstance = fakeLocationClient
        )

        viewModel.refreshLocation()
        advanceUntilIdle()

        val state = viewModel.locationState.value
        assertEquals(LocationVerificationStatus.TOO_FAR, state.status)
        assertFalse(state.isInsideRadius)
        assertTrue(state.distanceFromWorkMeters!! > allowedRadius)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun testLocationRefresh_whenPoorAccuracy_setsPoorAccuracyStatus() = runTest(testDispatcher) {
        val fakeLocationClient = FakeLocationClient(
            LocationResult.Success(
                latitude = siteLat,
                longitude = siteLng,
                accuracyMeters = 150.0f,
                timestamp = System.currentTimeMillis()
            )
        )

        val viewModel = WorkViewModel(
            application = Application(),
            locationClientInstance = fakeLocationClient
        )

        viewModel.refreshLocation()
        advanceUntilIdle()

        val state = viewModel.locationState.value
        assertEquals(LocationVerificationStatus.POOR_ACCURACY, state.status)
        assertFalse(state.isInsideRadius)
        assertTrue(state.errorMessage!!.contains("Weak GPS signal"))
    }

    @Test
    fun testLocationRefresh_whenPermissionDenied_setsPermissionRequired() = runTest(testDispatcher) {
        val fakeLocationClient = FakeLocationClient(LocationResult.PermissionDenied)

        val viewModel = WorkViewModel(
            application = Application(),
            locationClientInstance = fakeLocationClient
        )

        viewModel.refreshLocation()
        advanceUntilIdle()

        val state = viewModel.locationState.value
        assertEquals(LocationVerificationStatus.PERMISSION_REQUIRED, state.status)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun testLocationRefresh_whenGpsDisabled_setsGpsDisabledStatus() = runTest(testDispatcher) {
        val fakeLocationClient = FakeLocationClient(LocationResult.GpsDisabled)

        val viewModel = WorkViewModel(
            application = Application(),
            locationClientInstance = fakeLocationClient
        )

        viewModel.refreshLocation()
        advanceUntilIdle()

        val state = viewModel.locationState.value
        assertEquals(LocationVerificationStatus.GPS_DISABLED, state.status)
        assertTrue(state.errorMessage!!.contains("Location services are disabled"))
    }

    // ── 3. Repository Start Work with Real Coordinates ──

    @Test
    fun testWorkRepository_startWork_sendsCoordinatesAndParsesResponse() = runTest(testDispatcher) {
        var recordedRequest: StartWorkRpcRequest? = null

        val fakeApiService = createFakeApiService { request ->
            recordedRequest = request
            Response.success(
                WorkDto(
                    id = request.workId,
                    title = "Monthly Pest Control Service",
                    status = "IN_PROGRESS",
                    startTime = "2026-09-13T06:30:00Z",
                    latitude = siteLat,
                    longitude = siteLng,
                    allowedRadiusMeters = 150.0,
                    distanceFromWorkMeters = 25.0,
                    locationVerified = true
                )
            )
        }

        val repository = WorkRepository(fakeApiService, testDispatcher)
        val result = repository.startWork(10L, 19.1138, 72.8698, 12.0)

        assertTrue(result is NetworkResult.Success)
        val startedWork = (result as NetworkResult.Success).data
        assertNotNull(recordedRequest)
        assertEquals(10L, recordedRequest!!.workId)
        assertEquals(19.1138, recordedRequest!!.latitude, 0.0001)
        assertEquals(72.8698, recordedRequest!!.longitude, 0.0001)
        assertEquals(12.0, recordedRequest!!.accuracyMeters!!, 0.0001)

        assertEquals("10", startedWork.id)
        assertEquals(WorkStatus.IN_PROGRESS, startedWork.status)
        assertEquals("2026-09-13T06:30:00Z", startedWork.startTime)
        assertTrue(startedWork.locationVerified == true)
        assertEquals(25.0, startedWork.distanceFromWorkMeters!!, 0.001)
    }

    // ── 4. ViewModel Start Work Integration ──

    @Test
    fun testViewModel_startWork_whenLocationVerified_transitionsToInProgress() = runTest(testDispatcher) {
        val fakeLocationClient = FakeLocationClient(
            LocationResult.Success(
                latitude = siteLat,
                longitude = siteLng,
                accuracyMeters = 8.0f,
                timestamp = System.currentTimeMillis()
            )
        )

        val fakeApiService = createFakeApiService { request ->
            Response.success(
                WorkDto(
                    id = request.workId,
                    title = "Monthly Pest Control Service",
                    status = "IN_PROGRESS",
                    startTime = "10:30 AM",
                    latitude = siteLat,
                    longitude = siteLng,
                    allowedRadiusMeters = 150.0,
                    locationVerified = true
                )
            )
        }

        val repository = WorkRepository(fakeApiService, testDispatcher)
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repository,
            locationClientInstance = fakeLocationClient
        )

        viewModel.refreshLocation()
        advanceUntilIdle()

        var callbackFired = false
        viewModel.startWork { callbackFired = true }
        advanceUntilIdle()

        assertTrue(callbackFired)
        assertEquals(WorkStatus.IN_PROGRESS, viewModel.work.value.status)
        assertEquals("10:30 AM", viewModel.work.value.startTime)
    }

    @Test
    fun testViewModel_startWork_whenBackendRejects_showsErrorMessage() = runTest(testDispatcher) {
        val fakeLocationClient = FakeLocationClient(
            LocationResult.Success(
                latitude = siteLat,
                longitude = siteLng,
                accuracyMeters = 8.0f,
                timestamp = System.currentTimeMillis()
            )
        )

        val errorJson = "{\"status\":400,\"error\":\"Location Verification Failed\",\"message\":\"You are outside the permitted work location.\"}"
        val fakeApiService = createFakeApiService { _ ->
            Response.error(400, errorJson.toResponseBody("application/json".toMediaTypeOrNull()))
        }

        val repository = WorkRepository(fakeApiService, testDispatcher)
        val viewModel = WorkViewModel(
            application = Application(),
            repository = repository,
            locationClientInstance = fakeLocationClient
        )

        viewModel.refreshLocation()
        advanceUntilIdle()

        var callbackFired = false
        viewModel.startWork { callbackFired = true }
        advanceUntilIdle()

        assertFalse(callbackFired)
        assertNotEquals(WorkStatus.IN_PROGRESS, viewModel.work.value.status)
        assertNotNull(viewModel.errorMessage.value)
    }
}
