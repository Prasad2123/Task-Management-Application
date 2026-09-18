package com.example.taskmanagementapplication.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.taskmanagementapplication.data.local.LocalPhotoManager
import com.example.taskmanagementapplication.work.report.PdfReportGenerator
import java.io.File
import com.example.taskmanagementapplication.BuildConfig
import com.example.taskmanagementapplication.core.model.*
import com.example.taskmanagementapplication.data.dto.*
import com.example.taskmanagementapplication.data.local.TokenManager
import com.example.taskmanagementapplication.data.network.ApiService
import com.example.taskmanagementapplication.data.network.NetworkModule
import com.example.taskmanagementapplication.data.network.NetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant

/**
 * WorkRepository — connects Android UI directly to Supabase PostgREST, RPC, and Storage.
 * Completely eliminates Spring Boot / localhost / port 8080 dependencies.
 */
class WorkRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val context: Context? = null
) {
    constructor(apiService: ApiService, ioDispatcher: CoroutineDispatcher) : this(apiService, null, ioDispatcher, null)

    // ====================================================================
    // WORKS & WORKFLOW RPCs
    // ====================================================================

    private fun sanitizeApprovalUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url
        return if (url.contains("localhost")) {
            url.replace("http://localhost:4173", "https://taskmanagementwebsite1.netlify.app")
                .replace("https://localhost:4173", "https://taskmanagementwebsite1.netlify.app")
                .replace("http://localhost", "https://taskmanagementwebsite1.netlify.app")
        } else {
            url
        }
    }

    suspend fun getMyWorks(): NetworkResult<List<Work>> = safeCall {
        val response = apiService.getMyWorks()
        NetworkModule.toNetworkResult(response).mapSuccess { dtos ->
            dtos.map { it.toDomainModel() }.sortedWith(
                compareByDescending<Work> { it.createdAt ?: "" }
                    .thenByDescending { it.backendId ?: (it.id.toLongOrNull() ?: 0L) }
            )
        }
    }

    suspend fun getWork(workId: Long): NetworkResult<Work> = safeCall {
        val response = apiService.getWork("eq.$workId")
        val result = NetworkModule.toNetworkResult(response)
        result.mapSuccess { list ->
            if (list.isEmpty()) throw IOException("Work $workId not found in Supabase")
            list.first().toDomainModel()
        }
    }

    suspend fun startWork(
        workId: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double? = null
    ): NetworkResult<Work> = safeCall {
        val request = StartWorkRpcRequest(
            workId = workId,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters
        )
        val response = apiService.startWork(request)
        NetworkModule.toNetworkResult(response).mapSuccess { it.toDomainModel() }
    }

    suspend fun submitForReview(workId: Long): NetworkResult<Work> = safeCall {
        val request = WorkIdRpcRequest(workId = workId)
        val response = apiService.submitForReview(request)
        NetworkModule.toNetworkResult(response).mapSuccess { it.toDomainModel() }
    }

    suspend fun completeWork(workId: Long): NetworkResult<Work> = safeCall {
        val request = WorkIdRpcRequest(workId = workId)
        val response = apiService.completeWork(request)
        NetworkModule.toNetworkResult(response).mapSuccess { it.toDomainModel() }
    }

    suspend fun resumeWork(workId: Long): NetworkResult<Work> = safeCall {
        val request = WorkIdRpcRequest(workId = workId)
        val response = apiService.resumeWork(request)
        NetworkModule.toNetworkResult(response).mapSuccess { it.toDomainModel() }
    }

    // ====================================================================
    // CHECKLIST ITEMS
    // ====================================================================

    suspend fun getChecklist(workId: Long): NetworkResult<List<ChecklistItem>> = safeCall {
        val response = apiService.getChecklist("eq.$workId")
        NetworkModule.toNetworkResult(response).mapSuccess { dtos ->
            dtos.map { it.toDomainModel() }
        }
    }

    suspend fun updateChecklistItem(
        workId: Long,
        itemId: Long,
        completed: Boolean
    ): NetworkResult<ChecklistItem> = safeCall {
        val nowStr = if (completed) Instant.now().toString() else null
        val body = ChecklistItemUpdateBody(isCompleted = completed, completedAt = nowStr)
        val response = apiService.updateChecklistItem("eq.$itemId", body)
        val result = NetworkModule.toNetworkResult(response)
        result.mapSuccess { list ->
            if (list.isEmpty()) throw IOException("Checklist item $itemId not found")
            list.first().toDomainModel()
        }
    }

    // ====================================================================
    // MASTER TASKS
    // ====================================================================

    suspend fun getMasterTasks(): NetworkResult<List<MasterTask>> = safeCall {
        val response = apiService.getMasterTasks()
        NetworkModule.toNetworkResult(response).mapSuccess { dtos ->
            dtos.map { it.toDomainModel() }
        }
    }

    // ====================================================================
    // COMPANIES MASTER
    // ====================================================================

    suspend fun getCompanies(): NetworkResult<List<Company>> = safeCall {
        val response = apiService.getCompanies()
        NetworkModule.toNetworkResult(response).mapSuccess { dtos ->
            dtos.map { it.toDomainModel() }
        }
    }

    suspend fun createWorkWithChecklist(
        title: String,
        companyName: String,
        address: String,
        serviceBoyId: Long,
        pocId: Long,
        supervisorId: Long,
        masterTaskIds: List<Long>,
        scheduledDate: String? = null,
        googleMapsLink: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        allowedRadiusMeters: Double = 150.0
    ): NetworkResult<Work> = safeCall {
        val request = CreateWorkWithChecklistRpcRequest(
            title = title,
            companyName = companyName,
            address = address,
            serviceBoyId = serviceBoyId,
            pocId = pocId,
            supervisorId = supervisorId,
            masterTaskIds = masterTaskIds,
            scheduledDate = scheduledDate,
            googleMapsLink = googleMapsLink,
            latitude = latitude,
            longitude = longitude,
            allowedRadiusMeters = allowedRadiusMeters
        )
        val response = apiService.createWorkWithChecklist(request)
        NetworkModule.toNetworkResult(response).mapSuccess { it.toDomainModel() }
    }

    suspend fun getAllUsers(): NetworkResult<List<UserProfileDto>> = safeCall {
        val response = apiService.getAllUsers()
        NetworkModule.toNetworkResult(response)
    }

    // ====================================================================
    // ADDITIONAL WORK
    // ====================================================================

    suspend fun getAdditionalWork(workId: Long): NetworkResult<List<AdditionalWorkItem>> = safeCall {
        val response = apiService.getAdditionalWork("eq.$workId")
        NetworkModule.toNetworkResult(response).mapSuccess { dtos ->
            dtos.map { it.toDomainModel() }
        }
    }

    suspend fun createAdditionalWork(
        workId: Long,
        description: String,
        masterTaskId: Long? = null,
        taskLabel: String? = null,
        clientItemId: String? = null
    ): NetworkResult<AdditionalWorkItem> = safeCall {
        val finalLabel = taskLabel ?: description
        try {
            val request = AddAdditionalWorkRpcRequest(
                workId = workId,
                description = description,
                clientItemId = clientItemId,
                masterTaskId = masterTaskId,
                taskLabel = finalLabel
            )
            val rpcResponse = apiService.addAdditionalWork(request)
            if (rpcResponse.isSuccessful && rpcResponse.body() != null) {
                return@safeCall NetworkResult.Success(rpcResponse.body()!!.toDomainModel())
            }
        } catch (e: Exception) {
            // Fall back to direct PostgREST insert if RPC is not available in mock/test
        }

        val body = CreateAdditionalWorkBody(
            workId = workId,
            description = description,
            masterTaskId = masterTaskId,
            taskLabel = finalLabel,
            clientItemId = clientItemId
        )
        val response = apiService.createAdditionalWork(body)
        val result = NetworkModule.toNetworkResult(response)
        result.mapSuccess { list ->
            if (list.isEmpty()) throw IOException("Failed to create additional work")
            list.first().toDomainModel()
        }
    }

    suspend fun updateAdditionalWork(
        workId: Long,
        id: Long,
        description: String
    ): NetworkResult<AdditionalWorkItem> = safeCall {
        val response = apiService.updateAdditionalWork("eq.$id", mapOf("description" to description))
        val result = NetworkModule.toNetworkResult(response)
        result.mapSuccess { list ->
            if (list.isEmpty()) throw IOException("Failed to update additional work")
            list.first().toDomainModel()
        }
    }

    suspend fun deleteAdditionalWork(workId: Long, id: Long): NetworkResult<Unit> = safeCall {
        val response = apiService.deleteAdditionalWork("eq.$id")
        if (response.isSuccessful) NetworkResult.Success(Unit)
        else NetworkResult.Error(response.code(), response.message())
    }

    // ====================================================================
    // APPROVALS
    // ====================================================================

    suspend fun getApprovals(workId: Long): NetworkResult<List<ApprovalDto>> = safeCall {
        val response = apiService.getApprovals("eq.$workId")
        NetworkModule.toNetworkResult(response)
    }

    suspend fun pocApprove(workId: Long): NetworkResult<PocDecisionResponseDto> = safeCall {
        val token = tokenManager?.getToken()
        if (tokenManager != null && token.isNullOrBlank()) {
            return@safeCall NetworkResult.Error(
                code = 401,
                message = "Authentication session expired or missing. Please log in again as POC."
            )
        }
        val request = PocDecisionRpcRequest(workId = workId, decision = "APPROVED")
        val response = apiService.pocDecision(request)
        val result = NetworkModule.toNetworkResult(response)
        result.mapSuccess { dto ->
            dto.copy(supervisorApprovalUrl = sanitizeApprovalUrl(dto.supervisorApprovalUrl))
        }
    }

    suspend fun getSupervisorWebRequest(workId: Long): NetworkResult<SupervisorWebRequestDto?> = safeCall {
        val response = apiService.getSupervisorWebRequest("eq.$workId")
        NetworkModule.toNetworkResult(response).mapSuccess { list ->
            list.firstOrNull()
        }
    }

    suspend fun pocReject(workId: Long, reason: String): NetworkResult<PocDecisionResponseDto> = safeCall {
        val token = tokenManager?.getToken()
        if (tokenManager != null && token.isNullOrBlank()) {
            return@safeCall NetworkResult.Error(
                code = 401,
                message = "Authentication session expired or missing. Please log in again as POC."
            )
        }
        val request = PocDecisionRpcRequest(workId = workId, decision = "REJECTED", reason = reason)
        val response = apiService.pocDecision(request)
        NetworkModule.toNetworkResult(response)
    }

    // ====================================================================
    // NOTIFICATIONS
    // ====================================================================

    suspend fun getNotifications(): NetworkResult<List<AppNotification>> = safeCall {
        val response = apiService.getNotifications()
        NetworkModule.toNetworkResult(response).mapSuccess { dtos ->
            dtos.map { it.toDomainModel() }
        }
    }

    suspend fun markNotificationAsRead(id: Long): NetworkResult<Unit> = safeCall {
        val response = apiService.markNotificationAsRead("eq.$id")
        if (response.isSuccessful) NetworkResult.Success(Unit)
        else NetworkResult.Error(response.code(), response.message())
    }

    suspend fun markAllNotificationsAsRead(): NetworkResult<Unit> = safeCall {
        val response = apiService.markAllNotificationsAsRead()
        if (response.isSuccessful) NetworkResult.Success(Unit)
        else NetworkResult.Error(response.code(), response.message())
    }

    // ====================================================================
    // ACTIVITY
    // ====================================================================

    suspend fun getActivity(workId: Long): NetworkResult<List<ActivityEvent>> = safeCall {
        val response = apiService.getActivity("eq.$workId")
        NetworkModule.toNetworkResult(response).mapSuccess { dtos ->
            dtos.map { it.toDomainModel() }
        }
    }

    // ====================================================================
    // PHOTOS & STORAGE
    // ====================================================================

    suspend fun getPhotos(workId: Long): NetworkResult<List<WorkPhoto>> = safeCall {
        val response = apiService.getPhotos("eq.$workId")
        if (!response.isSuccessful) {
            val errResult = NetworkModule.toNetworkResult(response)
            return@safeCall errResult.mapSuccess { emptyList() }
        }
        val dtos = response.body() ?: emptyList()
        val photos = dtos.map { dto ->
            var finalUrl = dto.photoUrl
            val storageRef = dto.storageReference
            if (!storageRef.isNullOrBlank()) {
                try {
                    val signResp = apiService.createSignedPhotoUrl(storageRef)
                    if (signResp.isSuccessful && signResp.body() != null) {
                        val signedPath = signResp.body()!!.signedURL
                        finalUrl = if (signedPath.startsWith("http")) {
                            signedPath
                        } else {
                            val prefix = if (signedPath.startsWith("/storage/v1")) "" else "/storage/v1"
                            val normPath = if (signedPath.startsWith("/")) signedPath else "/$signedPath"
                            BuildConfig.SUPABASE_URL.trimEnd('/') + prefix + normPath
                        }
                    }
                } catch (e: Exception) {
                    // Fall back to original photoUrl
                }
            }
            dto.copy(photoUrl = finalUrl).toDomainModel()
        }
        NetworkResult.Success(photos)
    }

    suspend fun uploadPhoto(
        workId: Long,
        filePart: MultipartBody.Part,
        titlePart: RequestBody? = null,
        categoryPart: RequestBody? = null,
        captionPart: RequestBody? = null,
        clientPhotoIdPart: RequestBody? = null
    ): NetworkResult<WorkPhoto> = safeCall {
        val buffer = Buffer()
        filePart.body.writeTo(buffer)
        val fileBytes = buffer.readByteArray()

        val rawContentType = filePart.body.contentType()?.toString() ?: "image/jpeg"
        val cleanContentType = if (rawContentType.contains(";")) rawContentType.substringBefore(";") else rawContentType

        val titleString = titlePart?.let { bodyToString(it) } ?: "Evidence Photo"
        val categoryString = categoryPart?.let { bodyToString(it) } ?: "GENERAL"
        val captionString = captionPart?.let { bodyToString(it) }
        val clientPhotoIdString = clientPhotoIdPart?.let { bodyToString(it) }

        val timestamp = System.currentTimeMillis()
        val fileName = "work_${workId}_${timestamp}.jpg"
        val storagePath = "work_${workId}/${fileName}"

        // 1. Upload bytes directly to Supabase private Storage bucket: work-photos
        val photoRequestBody = fileBytes.toRequestBody(cleanContentType.toMediaTypeOrNull())
        val storageUploadResp = apiService.uploadPhotoToStorage(storagePath, photoRequestBody)
        if (!storageUploadResp.isSuccessful && storageUploadResp.code() != 200 && storageUploadResp.code() != 201) {
            val error = NetworkModule.parseError(storageUploadResp)
            return@safeCall NetworkResult.Error(
                code = storageUploadResp.code(),
                message = "Storage upload failed: ${error.message}"
            )
        }

        // 2. Generate signed URL for secure authenticated reading
        var signedUrl: String? = null
        try {
            val signResp = apiService.createSignedPhotoUrl(storagePath)
            if (signResp.isSuccessful && signResp.body() != null) {
                val rawSignedPath = signResp.body()!!.signedURL
                signedUrl = if (rawSignedPath.startsWith("http")) {
                    rawSignedPath
                } else {
                    BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1" +
                            (if (rawSignedPath.startsWith("/")) rawSignedPath else "/$rawSignedPath")
                }
            }
        } catch (e: Exception) {
            // Signed URL fallback
        }

        // 3. Insert metadata record in PostgREST public.work_photos table
        val currentUserId = tokenManager?.getUserId()
        val recordBody = CreatePhotoRecordBody(
            workId = workId,
            title = titleString,
            category = categoryString,
            caption = captionString,
            clientPhotoId = clientPhotoIdString,
            uploadedById = currentUserId,
            storageReference = storagePath,
            uploadStatus = "UPLOADED",
            fileName = fileName,
            contentType = cleanContentType,
            fileSize = fileBytes.size.toLong(),
            photoUrl = signedUrl
        )

        val createResp = apiService.createPhotoRecord(recordBody)
        val createResult = NetworkModule.toNetworkResult(createResp)
        createResult.mapSuccess { list ->
            if (list.isEmpty()) throw IOException("Failed to save photo record")
            list.first().toDomainModel()
        }
    }

    suspend fun deletePhoto(workId: Long, photoId: Long): NetworkResult<Unit> = safeCall {
        val response = apiService.deletePhotoRecord("eq.$photoId")
        if (response.isSuccessful) NetworkResult.Success(Unit)
        else NetworkResult.Error(response.code(), response.message())
    }

    suspend fun updatePhotoMetadata(
        workId: Long,
        photoId: Long,
        caption: String?,
        category: PhotoCategory?
    ): NetworkResult<WorkPhoto> = safeCall {
        val response = apiService.updatePhotoMetadata(
            "eq.$photoId",
            UpdatePhotoMetadataRequest(caption = caption, category = category?.name)
        )
        val result = NetworkModule.toNetworkResult(response)
        result.mapSuccess { list ->
            if (list.isEmpty()) throw IOException("Failed to update photo metadata")
            list.first().toDomainModel()
        }
    }

    // ====================================================================
    // AGGREGATED REFRESH
    // ====================================================================

    /**
     * Atomically refreshes the work entity and all related sub-resources from Supabase.
     */
    suspend fun refreshWork(workId: Long): NetworkResult<Work> = safeCall {
        val workResp = apiService.getWork("eq.$workId")
        val workResult = NetworkModule.toNetworkResult(workResp)
        if (workResult !is NetworkResult.Success) {
            return@safeCall workResult as NetworkResult<Work>
        }
        val workList = workResult.data
        if (workList.isEmpty()) {
            return@safeCall NetworkResult.Error(message = "Work $workId not found in Supabase")
        }
        val baseWork = workList.first().toDomainModel()

        // Fetch checklist
        val checklistResp = apiService.getChecklist("eq.$workId")
        val checklist = if (checklistResp.isSuccessful) {
            checklistResp.body()?.map { it.toDomainModel() } ?: emptyList()
        } else emptyList()

        // Fetch photos
        val photosResp = apiService.getPhotos("eq.$workId")
        val photos = if (photosResp.isSuccessful) {
            photosResp.body()?.map { it.toDomainModel() } ?: emptyList()
        } else emptyList()

        // Fetch approvals
        val approvalsResp = apiService.getApprovals("eq.$workId")
        val approvals = if (approvalsResp.isSuccessful) {
            approvalsResp.body() ?: emptyList()
        } else emptyList()

        val pocApproval = approvals.find { it.approverRole == "POC" }
        val supervisorApproval = approvals.find { it.approverRole == "SITE_SUPERVISOR" }

        val isPocApproved = when (pocApproval?.status) {
            "APPROVED" -> true
            "REJECTED" -> false
            else -> if (baseWork.status == WorkStatus.APPROVED || baseWork.status == WorkStatus.COMPLETED || baseWork.status == WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW) true else null
        }

        val isSupervisorApproved = when (supervisorApproval?.status) {
            "APPROVED" -> true
            "REJECTED" -> false
            else -> if (baseWork.status == WorkStatus.APPROVED || baseWork.status == WorkStatus.COMPLETED) true else null
        }

        // Fetch activity events (ORDER BY event_timestamp ASC)
        val activityResp = apiService.getActivity("eq.$workId", order = "event_timestamp.asc")
        val activityLog = if (activityResp.isSuccessful) {
            activityResp.body()?.map { it.toDomainModel() } ?: emptyList()
        } else emptyList()

        // Fetch supervisor web approval request to restore existing URL
        val webReqResp = apiService.getSupervisorWebRequest("eq.$workId")
        val webReq = if (webReqResp.isSuccessful) {
            webReqResp.body()?.firstOrNull()
        } else null

        val enrichedWork = baseWork.copy(
            checklist = checklist,
            photos = photos,
            activityLog = activityLog,
            supervisorApprovalUrl = sanitizeApprovalUrl(webReq?.approvalUrl),
            pocApproved = isPocApproved,
            pocApprovalTime = pocApproval?.decidedAt ?: baseWork.pocApprovalTime,
            pocRejectionReason = if (pocApproval?.status == "REJECTED") pocApproval.rejectionReason else null,
            supervisorApproved = isSupervisorApproved,
            supervisorApprovalTime = supervisorApproval?.decidedAt ?: baseWork.supervisorApprovalTime,
            supervisorRejectionReason = if (supervisorApproval?.status == "REJECTED") supervisorApproval.rejectionReason else null,
            readyForCompletion = (isPocApproved == true && isSupervisorApproved == true)
        )

        NetworkResult.Success(enrichedWork)
    }

    // ====================================================================
    // WORK REPORT & PDF (STEP 11)
    // ====================================================================

    suspend fun getWorkReport(workId: Long): NetworkResult<WorkReportDto> = safeCall {
        val response = apiService.getWorkReport("eq.$workId")
        val result = NetworkModule.toNetworkResult(response)
        result.mapSuccess { list ->
            if (list.isEmpty()) throw IOException("Work report not generated yet")
            list.first()
        }
    }

    suspend fun downloadWorkReport(workId: Long): NetworkResult<ResponseBody> = safeCall {
        // Try downloading existing report from storage first
        try {
            val response = apiService.downloadReportFromStorage("WorkReport_${workId}.pdf")
            if (response.isSuccessful && response.body() != null) {
                return@safeCall NetworkResult.Success(response.body()!!)
            }
        } catch (_: Exception) {}

        // 1. Fetch authoritative Work entity for PDF data (including approvals and timeline)
        val refreshedResult = refreshWork(workId)
        var work = if (refreshedResult is NetworkResult.Success) refreshedResult.data else {
            val workResult = getWork(workId)
            if (workResult is NetworkResult.Success) workResult.data else null
        }

        // Populate checklist items if needed
        if (work != null && work.checklist.isEmpty()) {
            val clResult = getChecklist(workId)
            if (clResult is NetworkResult.Success) {
                work = work.copy(checklist = clResult.data)
            }
        }

        // Populate activity events if needed
        if (work != null && work.activityLog.isEmpty()) {
            val actResult = getActivity(workId)
            if (actResult is NetworkResult.Success) {
                work = work.copy(activityLog = actResult.data)
            }
        }

        // Populate photo records from Supabase
        val photosResult = getPhotos(workId)
        val remotePhotos = if (photosResult is NetworkResult.Success) photosResult.data else emptyList()
        if (work != null) {
            work = work.copy(photos = remotePhotos)
        }

        // 2. Authoritatively resolve photo Bitmaps (Local App-Private Storage FIRST, Supabase Storage Fallback)
        val photoBitmaps = mutableListOf<Pair<WorkPhoto, Bitmap>>()
        val photosToProcess = work?.photos ?: emptyList()

        for (photo in photosToProcess) {
            var bitmap: Bitmap? = null

            // A. Check Local App-Private Storage first
            if (context != null) {
                val localFile = LocalPhotoManager.getLocalPhotoFile(
                    context = context,
                    workId = workId,
                    photoId = photo.id,
                    backendId = photo.backendId,
                    storageReference = photo.storageReference,
                    localUri = photo.localUri
                )
                if (localFile != null && localFile.exists() && localFile.length() > 0) {
                    try {
                        bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                    } catch (_: Exception) {}
                }
            }

            // B. Check direct localUri
            if (bitmap == null && !photo.localUri.isNullOrBlank()) {
                try {
                    val rawPath = if (photo.localUri.startsWith("file://")) {
                        android.net.Uri.parse(photo.localUri).path ?: photo.localUri
                    } else photo.localUri
                    val f = File(rawPath)
                    if (f.exists() && f.length() > 0) {
                        bitmap = BitmapFactory.decodeFile(f.absolutePath)
                    }
                } catch (_: Exception) {}
            }

            // C. Fallback: Download bytes from Supabase Storage using signed / remote URL
            if (bitmap == null && !photo.remoteUrl.isNullOrBlank()) {
                try {
                    val resp = apiService.streamBinaryDirectly(photo.remoteUrl)
                    if (resp.isSuccessful) {
                        resp.body()?.byteStream()?.use { stream ->
                            bitmap = BitmapFactory.decodeStream(stream)
                        }
                    }
                } catch (_: Exception) {}
            }

            val resolvedBitmap = bitmap
            if (resolvedBitmap != null) {
                photoBitmaps.add(Pair(photo, resolvedBitmap))
            }
        }

        // D. If remote returned 0 photos, scan app-private storage for any captured photos for this work
        if (photoBitmaps.isEmpty() && context != null) {
            val localFiles = LocalPhotoManager.getAllLocalPhotos(context, workId)
            for (file in localFiles) {
                try {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        val fallbackPhoto = WorkPhoto(
                            id = file.nameWithoutExtension,
                            title = "Site Inspection",
                            category = PhotoCategory.SITE_INSPECTION,
                            uploadedAt = com.example.taskmanagementapplication.core.util.DateTimeUtils.currentIndiaFormatted(),
                            localUri = file.absolutePath
                        )
                        photoBitmaps.add(Pair(fallbackPhoto, bmp))
                    }
                } catch (_: Exception) {}
            }
            if (photoBitmaps.isNotEmpty() && work != null) {
                work = work.copy(photos = photoBitmaps.map { it.first })
            }
        }

        // 3. Authoritatively generate genuine multi-page PDF document via PdfReportGenerator
        val pdfBytes = PdfReportGenerator.generate(workId, work, photoBitmaps)

        // 4. Upload generated PDF to Supabase Storage 'work-reports' bucket
        try {
            val requestBody = pdfBytes.toRequestBody("application/pdf".toMediaTypeOrNull())
            apiService.uploadReportToStorage("WorkReport_${workId}.pdf", requestBody)
        } catch (e: Exception) {
            // Non-critical if storage upload has network race; document is returned locally
        }

        NetworkResult.Success(pdfBytes.toResponseBody("application/pdf".toMediaTypeOrNull()))
    }

    private fun generateWorkReportPdf(workId: Long, work: Work?): ByteArray {
        return PdfReportGenerator.generate(workId, work)
    }

    private fun bodyToString(requestBody: RequestBody): String {
        return try {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: Exception) {
            ""
        }
    }

    // ====================================================================
    // HELPERS & RETRY
    // ====================================================================

    private suspend fun <T> safeCall(
        maxRetries: Int = 2,
        initialDelayMs: Long = 800L,
        block: suspend () -> NetworkResult<T>
    ): NetworkResult<T> {
        return withContext(ioDispatcher) {
            var currentDelay = initialDelayMs
            for (attempt in 0..maxRetries) {
                try {
                    return@withContext block()
                } catch (e: IOException) {
                    if (attempt == maxRetries) {
                        return@withContext NetworkResult.Error(
                            message = "Unable to connect to Supabase. Please check your connection.",
                            isNetworkError = true
                        )
                    }
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2
                } catch (e: Exception) {
                    return@withContext NetworkResult.Error(message = "An unexpected error occurred: ${e.message}")
                }
            }
            NetworkResult.Error(
                message = "Unable to connect to Supabase after retries. Please check your connection.",
                isNetworkError = true
            )
        }
    }

    // ====================================================================
    // DTO → DOMAIN MODEL MAPPERS
    // ====================================================================

    private fun WorkDto.toDomainModel(): Work = Work(
        id = this.id.toString(),
        title = this.title,
        companyName = this.companyName ?: "",
        address = this.address ?: "",
        serviceBoyName = this.serviceBoy?.name ?: "",
        serviceBoyPhone = this.serviceBoy?.phone,
        serviceBoyEmail = this.serviceBoy?.email,
        pocName = this.poc?.name ?: "",
        pocPhone = this.poc?.phone,
        pocEmail = this.poc?.email,
        supervisorName = this.supervisor?.name ?: "",
        supervisorPhone = this.supervisor?.phone,
        supervisorEmail = this.supervisor?.email,
        status = this.status.toWorkStatus(),
        scheduledDate = this.scheduledDate ?: "",
        startTime = this.startTime,
        endTime = this.completedAt,
        description = this.description ?: "",
        notes = this.notes,
        distance = if (this.distanceFromWorkMeters != null) {
            com.example.taskmanagementapplication.core.util.GeoUtils.formatDistance(this.distanceFromWorkMeters)
        } else "",
        latitude = this.latitude,
        longitude = this.longitude,
        googleMapsLink = this.googleMapsLink,
        allowedRadiusMeters = this.allowedRadiusMeters ?: 150.0,
        locationVerified = this.locationVerified,
        distanceFromWorkMeters = this.distanceFromWorkMeters,
        readyForCompletion = this.readyForCompletion ?: (this.status == "SUPERVISOR_APPROVED" || this.status == "READY_FOR_COMPLETION"),
        backendId = this.id,
        serviceBoyId = this.serviceBoyId ?: this.serviceBoy?.id,
        pocId = this.pocId ?: this.poc?.id,
        supervisorId = this.supervisorId ?: this.supervisor?.id,
        submittedForReviewAt = this.submittedAt,
        completedAt = this.completedAt,
        createdAt = this.createdAt,
        checklist = emptyList(),
        photos = emptyList(),
        pocApproved = when (this.status) {
            "POC_APPROVED", "SUPERVISOR_APPROVED", "COMPLETED" -> true
            "REJECTED" -> false
            else -> null
        },
        supervisorApproved = when (this.status) {
            "SUPERVISOR_APPROVED", "COMPLETED" -> true
            "REJECTED" -> false
            else -> null
        }
    )

    private fun ActivityEventDto.toDomainModel(): ActivityEvent = ActivityEvent(
        id = this.id.toString(),
        description = this.description,
        timestamp = this.eventTimestamp ?: "",
        isDone = true,
        latitude = this.latitude,
        longitude = this.longitude,
        accuracyMeters = this.accuracyMeters,
        eventType = this.eventType,
        performedByName = this.performedBy?.name
    )

    private fun ChecklistItemDto.toDomainModel(): ChecklistItem = ChecklistItem(
        id = this.id.toString(),
        title = this.taskLabel?.ifBlank { this.title } ?: this.title,
        description = this.description ?: "",
        isCompleted = this.completed,
        isAdditional = this.additional,
        completedAt = this.completedAt,
        completedById = this.completedById,
        completedByName = this.completedBy?.name,
        masterTaskId = this.masterTaskId,
        taskLabel = this.taskLabel,
        displayOrder = this.displayOrder,
        createdAt = this.createdAt
    )

    private fun AdditionalWorkDto.toDomainModel(): AdditionalWorkItem = AdditionalWorkItem(
        id = this.id.toString(),
        workId = this.workId.toString(),
        description = this.taskLabel?.ifBlank { this.description } ?: this.description,
        masterTaskId = this.masterTaskId,
        taskLabel = this.taskLabel,
        createdById = this.createdById,
        createdByName = this.createdBy?.name ?: "",
        createdAt = this.createdAt ?: ""
    )

    private fun MasterTaskDto.toDomainModel(): MasterTask = MasterTask(
        id = this.id,
        taskLabel = this.taskLabel,
        category = this.category ?: "GENERAL",
        displayOrder = this.displayOrder,
        isActive = this.isActive
    )

    private fun CompanyDto.toDomainModel(): Company = Company(
        id = this.id,
        companyName = this.companyName,
        address = this.address,
        latitude = this.latitude,
        longitude = this.longitude
    )

    private fun String.toWorkStatus(): WorkStatus = when (this) {
        "IN_PROGRESS" -> WorkStatus.IN_PROGRESS
        "SUBMITTED_FOR_REVIEW" -> WorkStatus.WAITING_FOR_POC_REVIEW
        "POC_APPROVED" -> WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW
        "SUPERVISOR_APPROVED" -> WorkStatus.APPROVED
        "COMPLETED" -> WorkStatus.COMPLETED
        "REJECTED" -> WorkStatus.REJECTED
        else -> WorkStatus.NOT_STARTED
    }

    private fun WorkPhotoDto.toDomainModel(): WorkPhoto {
        val categoryEnum = try {
            PhotoCategory.valueOf(this.category)
        } catch (e: Exception) {
            PhotoCategory.GENERAL
        }
        val statusEnum = when (this.uploadStatus) {
            "UPLOADING" -> PhotoUploadStatus.UPLOADING
            "FAILED" -> PhotoUploadStatus.FAILED
            "PENDING" -> PhotoUploadStatus.PENDING
            else -> PhotoUploadStatus.UPLOADED
        }
        val fullPhotoUrl = if (this.photoUrl != null) {
            if (this.photoUrl.startsWith("http")) this.photoUrl
            else {
                val prefix = if (this.photoUrl.startsWith("/storage/v1")) "" else "/storage/v1"
                val normPath = if (this.photoUrl.startsWith("/")) this.photoUrl else "/${this.photoUrl}"
                BuildConfig.SUPABASE_URL.trimEnd('/') + prefix + normPath
            }
        } else null

        return WorkPhoto(
            id = this.id.toString(),
            title = this.title,
            category = categoryEnum,
            uploadedAt = com.example.taskmanagementapplication.core.util.DateTimeUtils.formatToIndiaTime(this.createdAt),
            uploadStatus = statusEnum,
            caption = this.caption,
            uploadProgress = 1.0f,
            isSelected = false,
            gradientSeed = (this.id % 5).toInt() + 1,
            remoteUrl = fullPhotoUrl,
            backendId = this.id,
            storageReference = this.storageReference
        )
    }

    private fun NotificationDto.toDomainModel(): AppNotification {
        val timeDisplay = com.example.taskmanagementapplication.core.util.DateTimeUtils.formatToIndiaTime(this.createdAt)
        return AppNotification(
            id = this.id.toString(),
            title = this.title,
            message = this.message,
            timestamp = timeDisplay,
            isRead = this.isRead,
            relatedWorkId = this.workId?.toString()
        )
    }
}

// Extension to transform NetworkResult<T> to NetworkResult<R>
fun <T, R> NetworkResult<T>.mapSuccess(transform: (T) -> R): NetworkResult<R> {
    return when (this) {
        is NetworkResult.Success -> NetworkResult.Success(transform(this.data))
        is NetworkResult.Error -> this
        is NetworkResult.Loading -> NetworkResult.Loading
    }
}
