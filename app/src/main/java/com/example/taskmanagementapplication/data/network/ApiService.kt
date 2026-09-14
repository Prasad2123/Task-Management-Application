package com.example.taskmanagementapplication.data.network

import com.example.taskmanagementapplication.data.dto.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface defining Supabase Auth, PostgREST, RPC, and Storage endpoints.
 * Operates directly against online Supabase backend without Spring Boot / localhost.
 */
interface ApiService {

    // ====================================================================
    // AUTHENTICATION & PROFILES
    // ====================================================================

    @POST("auth/v1/token?grant_type=password")
    suspend fun login(@Body request: LoginRequest): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<SupabaseAuthResponse>

    @POST("rest/v1/rpc/get_current_user_profile")
    suspend fun getCurrentUserProfile(): Response<UserProfileDto>

    @GET("rest/v1/users")
    suspend fun getAllUsers(): Response<List<UserProfileDto>>

    // ====================================================================
    // WORKS & WORKFLOW RPCs
    // ====================================================================

    @POST("rest/v1/rpc/get_my_works")
    suspend fun getMyWorks(): Response<List<WorkDto>>

    @GET("rest/v1/works")
    suspend fun getWork(@Query("id") idFilter: String): Response<List<WorkDto>>

    @POST("rest/v1/rpc/start_work")
    suspend fun startWork(@Body request: StartWorkRpcRequest): Response<WorkDto>

    @POST("rest/v1/rpc/submit_for_review")
    suspend fun submitForReview(@Body request: WorkIdRpcRequest): Response<WorkDto>

    @POST("rest/v1/rpc/poc_decision")
    suspend fun pocDecision(@Body request: PocDecisionRpcRequest): Response<ApprovalDto>

    @POST("rest/v1/rpc/supervisor_decision")
    suspend fun supervisorDecision(@Body request: SupervisorDecisionRpcRequest): Response<ApprovalDto>

    @POST("rest/v1/rpc/complete_work")
    suspend fun completeWork(@Body request: WorkIdRpcRequest): Response<WorkDto>

    @POST("rest/v1/rpc/resume_work")
    suspend fun resumeWork(@Body request: WorkIdRpcRequest): Response<WorkDto>

    // ====================================================================
    // CHECKLIST ITEMS
    // ====================================================================

    @GET("rest/v1/work_checklist_items")
    suspend fun getChecklist(
        @Query("work_id") workIdFilter: String,
        @Query("order") order: String = "display_order.asc"
    ): Response<List<ChecklistItemDto>>

    @PATCH("rest/v1/work_checklist_items")
    @Headers("Prefer: return=representation")
    suspend fun updateChecklistItem(
        @Query("id") idFilter: String,
        @Body body: ChecklistItemUpdateBody
    ): Response<List<ChecklistItemDto>>

    // ====================================================================
    // ADDITIONAL WORK
    // ====================================================================

    @GET("rest/v1/additional_works")
    suspend fun getAdditionalWork(
        @Query("work_id") workIdFilter: String,
        @Query("order") order: String = "created_at.asc"
    ): Response<List<AdditionalWorkDto>>

    @POST("rest/v1/rpc/add_additional_work")
    suspend fun addAdditionalWork(
        @Body request: AddAdditionalWorkRpcRequest
    ): Response<AdditionalWorkDto>

    @POST("rest/v1/additional_works")
    @Headers("Prefer: return=representation")
    suspend fun createAdditionalWork(
        @Body body: CreateAdditionalWorkBody
    ): Response<List<AdditionalWorkDto>>

    @DELETE("rest/v1/additional_works")
    suspend fun deleteAdditionalWork(
        @Query("id") idFilter: String
    ): Response<Unit>

    @PATCH("rest/v1/additional_works")
    @Headers("Prefer: return=representation")
    suspend fun updateAdditionalWork(
        @Query("id") idFilter: String,
        @Body body: Map<String, String>
    ): Response<List<AdditionalWorkDto>>

    // ====================================================================
    // APPROVALS
    // ====================================================================

    @GET("rest/v1/approvals")
    suspend fun getApprovals(
        @Query("work_id") workIdFilter: String
    ): Response<List<ApprovalDto>>

    // ====================================================================
    // ACTIVITY TIMELINE
    // ====================================================================

    @GET("rest/v1/activity_events")
    suspend fun getActivity(
        @Query("work_id") workIdFilter: String,
        @Query("order") order: String = "event_timestamp.desc"
    ): Response<List<ActivityEventDto>>

    // ====================================================================
    // PHOTOS & STORAGE
    // ====================================================================

    @GET("rest/v1/work_photos")
    suspend fun getPhotos(
        @Query("work_id") workIdFilter: String,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<WorkPhotoDto>>

    @POST("storage/v1/object/work-photos/{path}")
    suspend fun uploadPhotoToStorage(
        @Path(value = "path", encoded = true) path: String,
        @Body photoBytes: RequestBody
    ): Response<ResponseBody>

    @POST("rest/v1/work_photos")
    @Headers("Prefer: return=representation")
    suspend fun createPhotoRecord(
        @Body record: CreatePhotoRecordBody
    ): Response<List<WorkPhotoDto>>

    @POST("storage/v1/object/sign/work-photos/{path}")
    suspend fun createSignedPhotoUrl(
        @Path(value = "path", encoded = true) path: String,
        @Body body: CreateSignedUrlBody = CreateSignedUrlBody(expiresIn = 3600)
    ): Response<SignedUrlResponse>

    @PATCH("rest/v1/work_photos")
    @Headers("Prefer: return=representation")
    suspend fun updatePhotoMetadata(
        @Query("id") idFilter: String,
        @Body body: UpdatePhotoMetadataRequest
    ): Response<List<WorkPhotoDto>>

    @DELETE("rest/v1/work_photos")
    suspend fun deletePhotoRecord(
        @Query("id") idFilter: String
    ): Response<Unit>

    @DELETE("storage/v1/object/work-photos/{path}")
    suspend fun deletePhotoFromStorage(
        @Path(value = "path", encoded = true) path: String
    ): Response<ResponseBody>

    // ====================================================================
    // NOTIFICATIONS
    // ====================================================================

    @GET("rest/v1/notifications")
    suspend fun getNotifications(
        @Query("order") order: String = "created_at.desc"
    ): Response<List<NotificationDto>>

    @PATCH("rest/v1/notifications")
    suspend fun markNotificationAsRead(
        @Query("id") idFilter: String,
        @Body body: Map<String, Boolean> = mapOf("is_read" to true)
    ): Response<Unit>

    @PATCH("rest/v1/notifications")
    suspend fun markAllNotificationsAsRead(
        @Query("is_read") isReadFilter: String = "eq.false",
        @Body body: Map<String, Boolean> = mapOf("is_read" to true)
    ): Response<Unit>

    // ====================================================================
    // WORK REPORTS & PDF
    // ====================================================================

    @GET("rest/v1/work_reports")
    suspend fun getWorkReport(
        @Query("work_id") workIdFilter: String
    ): Response<List<WorkReportDto>>

    @POST("storage/v1/object/work-reports/{path}")
    suspend fun uploadReportToStorage(
        @Path(value = "path", encoded = true) path: String,
        @Body reportBytes: RequestBody
    ): Response<ResponseBody>

    @Streaming
    @GET("storage/v1/object/work-reports/{path}")
    suspend fun downloadReportFromStorage(
        @Path(value = "path", encoded = true) path: String
    ): Response<ResponseBody>

    @POST("storage/v1/object/sign/work-reports/{path}")
    suspend fun createSignedReportUrl(
        @Path(value = "path", encoded = true) path: String,
        @Body body: CreateSignedUrlBody = CreateSignedUrlBody(expiresIn = 7200)
    ): Response<SignedUrlResponse>

    @Streaming
    @GET
    suspend fun streamBinaryDirectly(@Url fullUrl: String): Response<ResponseBody>
}
