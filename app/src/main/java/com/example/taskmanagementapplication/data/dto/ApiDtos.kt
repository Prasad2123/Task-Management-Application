package com.example.taskmanagementapplication.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ====================================================================
// AUTH DTOs (Supabase Auth & Profiles)
// ====================================================================

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String = "bearer",
    @Json(name = "expires_in") val expiresIn: Long = 3600,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "user") val user: SupabaseUser
)

@JsonClass(generateAdapter = true)
data class SupabaseUser(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String,
    @Json(name = "user_metadata") val userMetadata: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "id") val id: Long,
    @Json(name = "auth_user_id") val authUserId: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "role") val role: String,
    @Json(name = "is_active") val isActive: Boolean = true
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "tokenType") val tokenType: String = "Bearer",
    @Json(name = "expiresInMs") val expiresInMs: Long = 3600000,
    @Json(name = "userId") val userId: Long,
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "role") val role: String,
    @Json(name = "phone") val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class UserSummaryDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "role") val role: String
)

@JsonClass(generateAdapter = true)
data class ApiErrorDto(
    @Json(name = "status") val status: Int = 500,
    @Json(name = "error") val error: String? = null,
    @Json(name = "message") val message: String = "Server error",
    @Json(name = "msg") val msg: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null
)

// ====================================================================
// WORK DTOs & RPCs
// ====================================================================

@JsonClass(generateAdapter = true)
data class WorkDto(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "work_type") val workType: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "scheduled_date") val scheduledDate: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "service_boy_id") val serviceBoyId: Long? = null,
    @Json(name = "poc_id") val pocId: Long? = null,
    @Json(name = "supervisor_id") val supervisorId: Long? = null,
    @Json(name = "company_name") val companyName: String? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "allowed_radius_meters") val allowedRadiusMeters: Double? = null,
    @Json(name = "distanceFromWorkMeters") val distanceFromWorkMeters: Double? = null,
    @Json(name = "locationVerified") val locationVerified: Boolean? = null,
    @Json(name = "start_time") val startTime: String? = null,
    @Json(name = "submitted_at") val submittedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "readyForCompletion") val readyForCompletion: Boolean? = null,
    @Json(name = "google_maps_link") val googleMapsLink: String? = null,
    @Json(name = "serviceBoy") val serviceBoy: UserSummaryDto? = null,
    @Json(name = "poc") val poc: UserSummaryDto? = null,
    @Json(name = "supervisor") val supervisor: UserSummaryDto? = null
)

@JsonClass(generateAdapter = true)
data class StartWorkRpcRequest(
    @Json(name = "p_work_id") val workId: Long,
    @Json(name = "p_latitude") val latitude: Double,
    @Json(name = "p_longitude") val longitude: Double,
    @Json(name = "p_accuracy_meters") val accuracyMeters: Double? = null
)

@JsonClass(generateAdapter = true)
data class WorkIdRpcRequest(
    @Json(name = "p_work_id") val workId: Long
)

@JsonClass(generateAdapter = true)
data class PocDecisionRpcRequest(
    @Json(name = "p_work_id") val workId: Long,
    @Json(name = "p_decision") val decision: String,
    @Json(name = "p_reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class SupervisorDecisionRpcRequest(
    @Json(name = "p_work_id") val workId: Long,
    @Json(name = "p_decision") val decision: String,
    @Json(name = "p_reason") val reason: String? = null
)

// Legacy request dto for start work
@JsonClass(generateAdapter = true)
data class StartWorkRequestDto(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "accuracyMeters") val accuracyMeters: Double? = null
)

// ====================================================================
// MASTER TASKS DTOs
// ====================================================================

@JsonClass(generateAdapter = true)
data class MasterTaskDto(
    @Json(name = "id") val id: Long,
    @Json(name = "task_label") val taskLabel: String,
    @Json(name = "category") val category: String? = "GENERAL",
    @Json(name = "display_order") val displayOrder: Int = 0,
    @Json(name = "is_active") val isActive: Boolean = true
)

@JsonClass(generateAdapter = true)
data class CreateWorkWithChecklistRpcRequest(
    @Json(name = "p_title") val title: String,
    @Json(name = "p_company_name") val companyName: String,
    @Json(name = "p_address") val address: String,
    @Json(name = "p_service_boy_id") val serviceBoyId: Long,
    @Json(name = "p_poc_id") val pocId: Long,
    @Json(name = "p_supervisor_id") val supervisorId: Long,
    @Json(name = "p_master_task_ids") val masterTaskIds: List<Long>,
    @Json(name = "p_scheduled_date") val scheduledDate: String? = null,
    @Json(name = "p_google_maps_link") val googleMapsLink: String? = null,
    @Json(name = "p_latitude") val latitude: Double? = null,
    @Json(name = "p_longitude") val longitude: Double? = null,
    @Json(name = "p_allowed_radius_meters") val allowedRadiusMeters: Double = 150.0
)

// ====================================================================
// CHECKLIST DTOs
// ====================================================================

@JsonClass(generateAdapter = true)
data class ChecklistItemDto(
    @Json(name = "id") val id: Long,
    @Json(name = "work_id") val workId: Long,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "is_additional") val additional: Boolean = false,
    @Json(name = "is_completed") val completed: Boolean = false,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "completed_by_id") val completedById: Long? = null,
    @Json(name = "completed_by") val completedBy: UserSummaryDto? = null,
    @Json(name = "master_task_id") val masterTaskId: Long? = null,
    @Json(name = "task_label") val taskLabel: String? = null,
    @Json(name = "display_order") val displayOrder: Int = 0,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ChecklistItemUpdateBody(
    @Json(name = "is_completed") val isCompleted: Boolean,
    @Json(name = "completed_at") val completedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ChecklistUpdateRequest(
    @Json(name = "completed") val completed: Boolean?
)

// ====================================================================
// ADDITIONAL WORK DTOs
// ====================================================================

@JsonClass(generateAdapter = true)
data class AdditionalWorkDto(
    @Json(name = "id") val id: Long,
    @Json(name = "work_id") val workId: Long,
    @Json(name = "description") val description: String,
    @Json(name = "master_task_id") val masterTaskId: Long? = null,
    @Json(name = "task_label") val taskLabel: String? = null,
    @Json(name = "client_item_id") val clientItemId: String? = null,
    @Json(name = "created_by_id") val createdById: Long? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "createdBy") val createdBy: UserSummaryDto? = null
)

@JsonClass(generateAdapter = true)
data class CreateAdditionalWorkBody(
    @Json(name = "work_id") val workId: Long,
    @Json(name = "description") val description: String,
    @Json(name = "master_task_id") val masterTaskId: Long? = null,
    @Json(name = "task_label") val taskLabel: String? = null,
    @Json(name = "client_item_id") val clientItemId: String? = null,
    @Json(name = "created_by_id") val createdById: Long? = null
)

@JsonClass(generateAdapter = true)
data class AddAdditionalWorkRpcRequest(
    @Json(name = "p_work_id") val workId: Long,
    @Json(name = "p_description") val description: String? = null,
    @Json(name = "p_client_item_id") val clientItemId: String? = null,
    @Json(name = "p_master_task_id") val masterTaskId: Long? = null,
    @Json(name = "p_task_label") val taskLabel: String? = null
)

@JsonClass(generateAdapter = true)
data class AdditionalWorkRequest(
    @Json(name = "description") val description: String,
    @Json(name = "clientItemId") val clientItemId: String? = null
)

// ====================================================================
// APPROVAL DTOs
// ====================================================================

@JsonClass(generateAdapter = true)
data class ApprovalDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "work_id") val workId: Long,
    @Json(name = "approver_id") val approverId: Long? = null,
    @Json(name = "approver_role") val approverRole: String,
    @Json(name = "status") val status: String,
    @Json(name = "decided_at") val decidedAt: String? = null,
    @Json(name = "rejection_reason") val rejectionReason: String? = null
)

@JsonClass(generateAdapter = true)
data class ApprovalRequest(
    @Json(name = "decision") val decision: String? = null,
    @Json(name = "reason") val reason: String? = null
)

// ====================================================================
// ACTIVITY DTOs
// ====================================================================

@JsonClass(generateAdapter = true)
data class ActivityEventDto(
    @Json(name = "id") val id: Long,
    @Json(name = "work_id") val workId: Long,
    @Json(name = "event_type") val eventType: String,
    @Json(name = "description") val description: String,
    @Json(name = "performed_by_id") val performedById: Long? = null,
    @Json(name = "event_timestamp") val eventTimestamp: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "accuracy_meters") val accuracyMeters: Double? = null,
    @Json(name = "performedBy") val performedBy: UserSummaryDto? = null
)

// ====================================================================
// PHOTO DTOs & STORAGE
// ====================================================================

@JsonClass(generateAdapter = true)
data class WorkPhotoDto(
    @Json(name = "id") val id: Long,
    @Json(name = "work_id") val workId: Long,
    @Json(name = "title") val title: String,
    @Json(name = "category") val category: String = "GENERAL",
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "client_photo_id") val clientPhotoId: String? = null,
    @Json(name = "uploaded_by_id") val uploadedById: Long? = null,
    @Json(name = "storage_reference") val storageReference: String? = null,
    @Json(name = "upload_status") val uploadStatus: String = "UPLOADED",
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "content_type") val contentType: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null,
    @Json(name = "photo_url") val photoUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreatePhotoRecordBody(
    @Json(name = "work_id") val workId: Long,
    @Json(name = "title") val title: String,
    @Json(name = "category") val category: String,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "client_photo_id") val clientPhotoId: String? = null,
    @Json(name = "uploaded_by_id") val uploadedById: Long? = null,
    @Json(name = "storage_reference") val storageReference: String? = null,
    @Json(name = "upload_status") val uploadStatus: String = "UPLOADED",
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "content_type") val contentType: String? = "image/jpeg",
    @Json(name = "file_size") val fileSize: Long? = null,
    @Json(name = "photo_url") val photoUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateSignedUrlBody(
    @Json(name = "expiresIn") val expiresIn: Int = 3600
)

@JsonClass(generateAdapter = true)
data class SignedUrlResponse(
    @Json(name = "signedURL") val signedURL: String
)

@JsonClass(generateAdapter = true)
data class UpdatePhotoMetadataRequest(
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "category") val category: String? = null
)

// ====================================================================
// NOTIFICATION DTOs
// ====================================================================

@JsonClass(generateAdapter = true)
data class NotificationDto(
    @Json(name = "id") val id: Long,
    @Json(name = "user_id") val userId: Long? = null,
    @Json(name = "work_id") val workId: Long? = null,
    @Json(name = "type") val type: String,
    @Json(name = "title") val title: String,
    @Json(name = "message") val message: String,
    @Json(name = "is_read") val isRead: Boolean = false,
    @Json(name = "read_at") val readAt: String? = null,
    @Json(name = "delivery_status") val deliveryStatus: String? = null,
    @Json(name = "created_at") val createdAt: String
)

// ====================================================================
// WORK REPORT DTOs
// ====================================================================

@JsonClass(generateAdapter = true)
data class WorkReportDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "work_id") val workId: Long,
    @Json(name = "report_number") val reportNumber: String,
    @Json(name = "storage_reference") val storageReference: String? = null,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "content_type") val contentType: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null,
    @Json(name = "generated_at") val generatedAt: String? = null,
    @Json(name = "created_by") val createdBy: Long? = null,
    @Json(name = "version") val version: Int? = null,
    @Json(name = "downloadUrl") val downloadUrl: String? = null,
    @Json(name = "duration") val duration: String? = null,
    @Json(name = "workTitle") val workTitle: String? = null,
    @Json(name = "clientName") val clientName: String? = null,
    @Json(name = "completedAt") val completedAt: String? = null
)
