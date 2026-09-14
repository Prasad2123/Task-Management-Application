package com.example.taskmanagementapplication.work.viewmodel

import com.example.taskmanagementapplication.core.model.PhotoCategory
import com.example.taskmanagementapplication.core.model.PhotoUploadStatus
import com.example.taskmanagementapplication.core.model.WorkPhoto
import com.example.taskmanagementapplication.data.dto.*
import com.example.taskmanagementapplication.data.network.ApiService
import com.example.taskmanagementapplication.data.network.NetworkResult
import com.example.taskmanagementapplication.data.repository.WorkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoFlowTest {

    private lateinit var viewModel: WorkViewModel

    @Before
    fun setUp() {
        viewModel = WorkViewModel()
    }

    @Test
    fun testInitialPhotosState_fromDemoWork() {
        val work = viewModel.work.value
        val photos = viewModel.getPhotos(work)

        assertNotNull(photos)
        assertEquals(5, photos.size)
        assertEquals(5, viewModel.getTotalPhotosCount(work))
        assertEquals(3, viewModel.getUploadedPhotosCount(work))
        assertEquals(1, viewModel.getUploadingPhotosCount(work))
        assertEquals(1, viewModel.getFailedPhotosCount(work))
        assertEquals(0, viewModel.getPendingPhotosCount(work))
    }

    @Test
    fun testAddPhotos_increasesCountAndSetsUploading() {
        val initialCount = viewModel.getTotalPhotosCount(viewModel.work.value)
        val newPhoto = WorkPhoto(
            id = "PHOTO_TEST_1",
            title = "Evidence Test Photo",
            category = PhotoCategory.EQUIPMENT_CHECK,
            uploadedAt = "Just now",
            uploadStatus = PhotoUploadStatus.UPLOADING,
            caption = "Test Caption"
        )

        viewModel.addPhotos(listOf(newPhoto))

        val workAfter = viewModel.work.value
        val photosAfter = viewModel.getPhotos(workAfter)

        assertEquals(initialCount + 1, photosAfter.size)
        assertTrue(photosAfter.any { it.id == "PHOTO_TEST_1" })
    }

    @Test
    fun testDeletePhoto_removesPhotoSuccessfully() {
        val work = viewModel.work.value
        val firstPhoto = work.photos.first()

        val deleted = viewModel.deletePhoto(firstPhoto.id)
        assertTrue(deleted)

        val workAfter = viewModel.work.value
        assertFalse(workAfter.photos.any { it.id == firstPhoto.id })
    }

    @Test
    fun testDeletePhoto_nonexistentId_returnsFalse() {
        val deleted = viewModel.deletePhoto("NONEXISTENT_ID_9999")
        assertFalse(deleted)
    }

    @Test
    fun testRetryPhotoUpload_updatesState() {
        val failedPhoto = WorkPhoto(
            id = "FAILED_PHOTO_1",
            title = "Failed Evidence",
            category = PhotoCategory.SAFETY_PPE,
            uploadedAt = "10 mins ago",
            uploadStatus = PhotoUploadStatus.FAILED
        )

        viewModel.addPhotos(listOf(failedPhoto))
        viewModel.retryPhotoUpload("FAILED_PHOTO_1")

        val workAfter = viewModel.work.value
        val photo = workAfter.photos.find { it.id == "FAILED_PHOTO_1" }
        assertNotNull(photo)
        assertNotEquals(PhotoUploadStatus.FAILED, photo?.uploadStatus)
    }

    @Test
    fun testUpdatePhotoMetadata() {
        val work = viewModel.work.value
        val photo = work.photos.first()

        viewModel.updatePhotoMetadata(
            photoId = photo.id,
            caption = "Updated Note From Test",
            category = PhotoCategory.ADDITIONAL_WORK
        )

        val workAfter = viewModel.work.value
        val updatedPhoto = workAfter.photos.find { it.id == photo.id }
        assertNotNull(updatedPhoto)
        assertEquals("Updated Note From Test", updatedPhoto?.caption)
        assertEquals(PhotoCategory.ADDITIONAL_WORK, updatedPhoto?.category)
    }

    @Test
    fun testCreateMockPhoto_helper() {
        val photo = viewModel.createMockPhoto("Test Title", PhotoCategory.TREATMENT_APPLICATION, "Sample Caption")
        assertEquals("Test Title", photo.title)
        assertEquals(PhotoCategory.TREATMENT_APPLICATION, photo.category)
        assertEquals("Sample Caption", photo.caption)
        assertEquals(PhotoUploadStatus.UPLOADING, photo.uploadStatus)
    }

    @Test
    fun testWorkRepository_getPhotos_mapsDomainModelCorrectly() = runTest {
        val fakeApiService = object : ApiService by (java.lang.reflect.Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun getPhotos(workIdFilter: String, order: String): Response<List<WorkPhotoDto>> {
                return Response.success(
                    listOf(
                        WorkPhotoDto(
                            id = 42L,
                            workId = 100L,
                            title = "Sprayer Nozzle Check",
                            category = "EQUIPMENT_CHECK",
                            caption = "Cleaned nozzle",
                            uploadStatus = "UPLOADED",
                            fileName = "nozzle.jpg",
                            contentType = "image/jpeg",
                            fileSize = 2048L,
                            photoUrl = "/storage/v1/object/work-photos/100/nozzle.jpg",
                            createdAt = "2026-09-13T06:00:00Z"
                        )
                    )
                )
            }
        }

        val repository = WorkRepository(fakeApiService)
        val result = repository.getPhotos(100L)

        assertTrue(result is NetworkResult.Success)
        val photos = (result as NetworkResult.Success).data
        assertEquals(1, photos.size)

        val photo = photos.first()
        assertEquals("42", photo.id)
        assertEquals(42L, photo.backendId)
        assertEquals("Sprayer Nozzle Check", photo.title)
        assertEquals(PhotoCategory.EQUIPMENT_CHECK, photo.category)
        assertEquals("Cleaned nozzle", photo.caption)
        assertEquals(PhotoUploadStatus.UPLOADED, photo.uploadStatus)
        assertNotNull(photo.remoteUrl)
        assertTrue(photo.remoteUrl!!.contains("nozzle.jpg"))
    }

    @Test
    fun testWorkRepository_uploadPhoto_success() = runTest {
        val fakeApiService = object : ApiService by (java.lang.reflect.Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun uploadPhotoToStorage(path: String, photoBytes: RequestBody): Response<ResponseBody> {
                return Response.success("".toResponseBody("application/json".toMediaTypeOrNull()))
            }
            override suspend fun createPhotoRecord(record: CreatePhotoRecordBody): Response<List<WorkPhotoDto>> {
                return Response.success(
                    listOf(
                        WorkPhotoDto(
                            id = 99L,
                            workId = record.workId,
                            title = record.title,
                            category = record.category,
                            caption = record.caption,
                            uploadStatus = "UPLOADED",
                            photoUrl = "/storage/v1/object/work-photos/99.jpg"
                        )
                    )
                )
            }
        }

        val repository = WorkRepository(fakeApiService)
        val dummyPart = MultipartBody.Part.createFormData(
            "file", "gate.jpg", "bytes".toRequestBody("image/jpeg".toMediaTypeOrNull())
        )
        val result = repository.uploadPhoto(100L, dummyPart)

        assertTrue(result is NetworkResult.Success)
        val photo = (result as NetworkResult.Success).data
        assertEquals(99L, photo.backendId)
        assertEquals(PhotoCategory.GENERAL, photo.category)
        assertEquals("Evidence Photo", photo.title)
    }

    @Test
    fun testWorkRepository_uploadPhoto_failure() = runTest {
        val fakeApiService = object : ApiService by (java.lang.reflect.Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun uploadPhotoToStorage(path: String, photoBytes: RequestBody): Response<ResponseBody> {
                return Response.error<ResponseBody>(413, "{\"message\":\"File size exceeds 10MB\"}".toResponseBody("application/json".toMediaTypeOrNull()))
            }
        }

        val repository = WorkRepository(fakeApiService)
        val dummyPart = MultipartBody.Part.createFormData(
            "file", "large.jpg", "bytes".toRequestBody("image/jpeg".toMediaTypeOrNull())
        )
        val result = repository.uploadPhoto(100L, dummyPart)

        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals(413, error.code)
    }

    @Test
    fun testWorkRepository_deletePhoto_success() = runTest {
        val fakeApiService = object : ApiService by (java.lang.reflect.Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, _, _ -> null } as ApiService) {
            override suspend fun deletePhotoRecord(idFilter: String): Response<Unit> {
                return Response.success(Unit)
            }
        }

        val repository = WorkRepository(fakeApiService)
        val result = repository.deletePhoto(100L, 42L)

        assertTrue(result is NetworkResult.Success)
    }
}
