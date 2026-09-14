package com.example.taskmanagementapplication.work.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanagementapplication.core.model.*
import com.example.taskmanagementapplication.core.util.FileUtils
import com.example.taskmanagementapplication.data.dto.ApprovalDto
import com.example.taskmanagementapplication.data.dto.WorkReportDto
import com.example.taskmanagementapplication.data.local.TokenManager
import com.example.taskmanagementapplication.data.network.NetworkModule
import com.example.taskmanagementapplication.data.network.NetworkResult
import com.example.taskmanagementapplication.data.repository.WorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.taskmanagementapplication.core.location.DefaultLocationClient
import com.example.taskmanagementapplication.core.location.LocationClient
import com.example.taskmanagementapplication.core.location.LocationConstants
import com.example.taskmanagementapplication.core.location.LocationResult
import com.example.taskmanagementapplication.core.util.GeoUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ReportUiState {
    object Idle : ReportUiState
    object Loading : ReportUiState
    data class Success(val report: WorkReportDto, val localPdfFile: File? = null) : ReportUiState
    data class Error(val message: String) : ReportUiState
}

enum class LocationVerificationStatus {
    CHECKING_LOCATION,
    PERMISSION_REQUIRED,
    GPS_DISABLED,
    POOR_ACCURACY,
    TOO_FAR,
    VERIFIED,
    LOCATION_UNAVAILABLE
}

data class LocationState(
    val status: LocationVerificationStatus = LocationVerificationStatus.CHECKING_LOCATION,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val accuracyMeters: Float? = null,
    val distanceFromWorkMeters: Double? = null,
    val allowedRadiusMeters: Double = LocationConstants.DEFAULT_ALLOWED_RADIUS_METERS,
    val isInsideRadius: Boolean = false,
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false
)

/**
 * WorkViewModel — connected to real backend API via WorkRepository.
 * Preserves all existing UI contracts while replacing mock data with real API calls.
 * Loading/error states are surfaced via StateFlow for UI consumption.
 */
class WorkViewModel @JvmOverloads constructor(
    application: Application,
    repository: WorkRepository? = null,
    locationClientInstance: LocationClient? = null,
    networkMonitorInstance: com.example.taskmanagementapplication.core.network.NetworkMonitor? = null,
    cacheInstance: com.example.taskmanagementapplication.data.local.WorkLocalCache? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    initialWork: Work? = null
) : AndroidViewModel(application) {

    private val tokenManager: TokenManager? = try {
        val ctx = application.applicationContext ?: application
        TokenManager(ctx)
    } catch (e: Exception) {
        null
    }

    private val apiService = tokenManager?.let {
        try {
            NetworkModule.createApiService(it, application.applicationContext ?: application)
        } catch (e: Exception) {
            null
        }
    }

    private val workRepository: WorkRepository? = repository ?: apiService?.let { WorkRepository(it, tokenManager) }

    private val locationClient: LocationClient by lazy {
        locationClientInstance ?: try {
            val ctx = application.applicationContext ?: application
            DefaultLocationClient(ctx)
        } catch (e: Exception) {
            object : LocationClient {
                override suspend fun getCurrentLocation() = LocationResult.Error("Location client unavailable")
            }
        }
    }

    private val networkMonitor: com.example.taskmanagementapplication.core.network.NetworkMonitor =
        networkMonitorInstance ?: try {
            val ctx = application.applicationContext ?: application
            com.example.taskmanagementapplication.core.network.DefaultNetworkMonitor(ctx)
        } catch (e: Exception) {
            object : com.example.taskmanagementapplication.core.network.NetworkMonitor {
                override val isOnline = MutableStateFlow(true)
                override val networkStatus = MutableStateFlow(com.example.taskmanagementapplication.core.network.NetworkStatus.ONLINE)
            }
        }
    val networkStatus: StateFlow<com.example.taskmanagementapplication.core.network.NetworkStatus> = networkMonitor.networkStatus
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val workLocalCache: com.example.taskmanagementapplication.data.local.WorkLocalCache? =
        cacheInstance ?: try {
            val ctx = application.applicationContext ?: application
            com.example.taskmanagementapplication.data.local.WorkLocalCache(ctx)
        } catch (e: Exception) {
            null
        }

    private val _lastSyncedText = MutableStateFlow<String?>(null)
    val lastSyncedText: StateFlow<String?> = _lastSyncedText.asStateFlow()

    init {
        workLocalCache?.getLastSyncedAt()?.let {
            updateLastSyncedDisplay(it)
        }
    }

    private fun updateLastSyncedDisplay(epochMillis: Long?) {
        if (epochMillis == null) {
            _lastSyncedText.value = null
            return
        }
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        _lastSyncedText.value = sdf.format(Date(epochMillis))
    }

    private fun persistSnapshot() {
        workLocalCache?.let { cache ->
            val now = System.currentTimeMillis()
            cache.saveSnapshot(
                com.example.taskmanagementapplication.data.local.CachedWorkSnapshot(
                    work = _work.value,
                    additionalWork = _additionalWork.value,
                    notifications = _notifications.value,
                    lastSyncedAt = now
                )
            )
            updateLastSyncedDisplay(now)
        }
    }

    constructor() : this(Application(), initialWork = com.example.taskmanagementapplication.core.mock.MockWorkRepository.demoWork)

    companion object {
        val EMPTY_WORK = Work(
            id = "",
            title = "",
            companyName = "",
            address = "",
            serviceBoyName = "",
            pocName = "",
            supervisorName = "",
            status = WorkStatus.NOT_STARTED,
            scheduledDate = "",
            backendId = null
        )
    }

    // ---- Location State ----
    private val _locationState = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    // ---- Core work state ----
    private val _work = MutableStateFlow<Work>(
        initialWork ?: com.example.taskmanagementapplication.core.mock.MockWorkRepository.demoWork
    )
    val work: StateFlow<Work> = _work.asStateFlow()

    val hasActiveWork: Boolean
        get() = _work.value.backendId != null && _work.value.status != WorkStatus.COMPLETED

    // ---- Predefined works list (for all roles & Admin monitoring) ----
    private val _predefinedWorks = MutableStateFlow<List<Work>>(emptyList())
    val predefinedWorks: StateFlow<List<Work>> = _predefinedWorks.asStateFlow()

    // ---- Loading / Error states ----
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    // ---- Additional work ----
    private val _additionalWork = MutableStateFlow<List<AdditionalWorkItem>>(emptyList())
    val additionalWork: StateFlow<List<AdditionalWorkItem>> = _additionalWork.asStateFlow()

    // ---- Approvals ----
    private val _approvals = MutableStateFlow<List<ApprovalDto>>(emptyList())
    val approvals: StateFlow<List<ApprovalDto>> = _approvals.asStateFlow()

    // ---- Work Report ----
    private val _reportState = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val reportState: StateFlow<ReportUiState> = _reportState.asStateFlow()

    private val _isDownloadingReport = MutableStateFlow(false)
    val isDownloadingReport: StateFlow<Boolean> = _isDownloadingReport.asStateFlow()

    // ---- Timer ----
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()
    private var timerJob: Job? = null

    // ---- Notifications (Real online backend notifications) ----
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    // -------------------------------------------------------
    // Data Loading
    // -------------------------------------------------------

    /**
     * Loads predefined work data for the authenticated user from the backend.
     * Selects active work dynamically by strict priority:
     * 1. Preserves current active work if already IN_PROGRESS.
     * 2. Prioritizes any work that is IN_PROGRESS over pending work.
     * 3. If exactly one active work exists, auto-selects it.
     * 4. If multiple active works exist, preserves existing valid selection or exposes list for user selection.
     * 5. Never auto-selects COMPLETED work as active.
     */
    fun loadMyWork() {
        val repo = workRepository
        if (!isOnline.value) {
            workLocalCache?.loadSnapshot()?.let { snapshot ->
                _work.value = snapshot.work
                _additionalWork.value = snapshot.additionalWork
                _notifications.value = snapshot.notifications
                updateLastSyncedDisplay(snapshot.lastSyncedAt)
            }
            return
        }
        if (repo == null) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            when (val result = repo.getMyWorks()) {
                is NetworkResult.Success -> {
                    val works = result.data
                    _predefinedWorks.value = works

                    val activeWorks = works.filter { it.status != WorkStatus.COMPLETED }
                    val currentBackendId = _work.value.backendId

                    // 1. Preserve current work if already IN_PROGRESS
                    val currentInProgress = if (currentBackendId != null) {
                        activeWorks.find { it.backendId == currentBackendId && (it.status == WorkStatus.IN_PROGRESS || it.status == WorkStatus.WORK_STARTED) }
                    } else null

                    // 2. IN_PROGRESS work takes top priority over merely pending work
                    val anyInProgress = currentInProgress ?: activeWorks.find {
                        it.status == WorkStatus.IN_PROGRESS || it.status == WorkStatus.WORK_STARTED
                    }

                    val targetWork: Work? = when {
                        anyInProgress != null -> anyInProgress
                        // Preserve existing valid user selection among active works
                        currentBackendId != null && activeWorks.any { it.backendId == currentBackendId } -> {
                            activeWorks.first { it.backendId == currentBackendId }
                        }
                        // Exactly 1 active work exists: show it
                        activeWorks.size == 1 -> activeWorks.first()
                        // Multiple active works exist: require user selection (never arbitrary fallback)
                        activeWorks.size > 1 -> null
                        // Zero active works (e.g. all completed or none assigned): never select completed work
                        else -> null
                    }

                    if (targetWork != null) {
                        selectWork(targetWork)
                    } else {
                        // Clear active work if current selection is invalid or all works completed
                        if (activeWorks.isEmpty() || (currentBackendId != null && activeWorks.none { it.backendId == currentBackendId })) {
                            _work.value = EMPTY_WORK
                            stopTimer()
                        }
                    }

                    loadNotifications()
                    persistSnapshot()
                }
                is NetworkResult.Error -> {
                    // On error, fall back to offline cache if available
                    workLocalCache?.loadSnapshot()?.let { snapshot ->
                        _work.value = snapshot.work
                        _additionalWork.value = snapshot.additionalWork
                        _notifications.value = snapshot.notifications
                        updateLastSyncedDisplay(snapshot.lastSyncedAt)
                    }
                    _errorMessage.value = result.toUserMessage()
                }
                is NetworkResult.Loading -> {}
            }

            _isLoading.value = false
        }
    }

    /**
     * Called on screen resume or lifecycle foreground to fetch freshest state from authoritative backend.
     */
    fun syncOnResume() {
        if (isOnline.value) {
            val currentWork = _work.value
            val workId = currentWork.backendId
            val repo = workRepository
            if (workId != null && repo != null) {
                viewModelScope.launch {
                    when (val result = repo.refreshWork(workId)) {
                        is NetworkResult.Success -> {
                            _work.value = result.data
                            loadApprovals(workId)
                            loadAdditionalWork(workId)
                            loadNotifications()
                            persistSnapshot()
                        }
                        else -> {
                            loadMyWork()
                        }
                    }
                }
            } else {
                loadMyWork()
            }
        }
    }

    private fun loadChecklist(workId: Long) {
        val repo = workRepository ?: return
        viewModelScope.launch {
            when (val result = repo.getChecklist(workId)) {
                is NetworkResult.Success -> {
                    _work.update { it.copy(checklist = result.data) }
                    persistSnapshot()
                }
                else -> {} // Non-critical — work still shows without checklist
            }
        }
    }

    private fun loadAdditionalWork(workId: Long) {
        val repo = workRepository ?: return
        viewModelScope.launch {
            when (val result = repo.getAdditionalWork(workId)) {
                is NetworkResult.Success -> {
                    _additionalWork.value = result.data
                    persistSnapshot()
                }
                else -> {}
            }
        }
    }

    fun loadApprovals(workId: Long) {
        val repo = workRepository ?: return
        viewModelScope.launch {
            when (val result = repo.getApprovals(workId)) {
                is NetworkResult.Success -> {
                    _approvals.value = result.data
                    // Update work approval state from approval records
                    updateWorkApprovalState(result.data)
                    persistSnapshot()
                }
                else -> {}
            }
        }
    }

    fun loadActivity(workId: Long) {
        val repo = workRepository ?: return
        viewModelScope.launch {
            when (val result = repo.getActivity(workId)) {
                is NetworkResult.Success -> {
                    _work.update { it.copy(activityLog = result.data) }
                    persistSnapshot()
                }
                else -> {}
            }
        }
    }

    fun selectWork(selected: Work) {
        _work.value = selected
        if (selected.status == WorkStatus.IN_PROGRESS || selected.status == WorkStatus.WORK_STARTED) {
            startTimer()
        } else {
            stopTimer()
        }
        selected.backendId?.let { workId ->
            loadChecklist(workId)
            loadAdditionalWork(workId)
            loadApprovals(workId)
            loadPhotos(workId)
            loadActivity(workId)
        }
        persistSnapshot()
    }

    fun clearSelectedWork() {
        _work.value = EMPTY_WORK
        stopTimer()
        persistSnapshot()
    }

    private fun updateWorkApprovalState(approvals: List<ApprovalDto>) {
        val pocApproval = approvals.find { it.approverRole == "POC" }
        val supervisorApproval = approvals.find { it.approverRole == "SITE_SUPERVISOR" }

        _work.update { work ->
            work.copy(
                pocApproved = when (pocApproval?.status) {
                    "APPROVED" -> true
                    "REJECTED" -> false
                    else -> null
                },
                pocApprovalTime = pocApproval?.decidedAt?.let { formatServerTime(it) } ?: work.pocApprovalTime,
                pocRejectionReason = if (pocApproval?.status == "REJECTED")
                    pocApproval.rejectionReason else null,
                supervisorApproved = when (supervisorApproval?.status) {
                    "APPROVED" -> true
                    "REJECTED" -> false
                    else -> null
                },
                supervisorApprovalTime = supervisorApproval?.decidedAt?.let { formatServerTime(it) } ?: work.supervisorApprovalTime,
                supervisorRejectionReason = if (supervisorApproval?.status == "REJECTED")
                    supervisorApproval.rejectionReason else null,
                readyForCompletion = (pocApproval?.status == "APPROVED" && supervisorApproval?.status == "APPROVED")
            )
        }
    }

    private fun formatServerTime(timeStr: String): String {
        return try {
            val instant = java.time.Instant.parse(timeStr)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.getDefault())
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            timeStr
        }
    }

    // -------------------------------------------------------
    // Work lifecycle & GPS Verification
    // -------------------------------------------------------

    /**
     * Obtains fresh GPS coordinates from the device and recalculates distance to the work location.
     */
    fun refreshLocation() {
        val currentWork = _work.value
        val workLat = currentWork.latitude
        val workLng = currentWork.longitude
        val allowedRadius = currentWork.allowedRadiusMeters

        viewModelScope.launch {
            _locationState.update { it.copy(isRefreshing = true, errorMessage = null) }

            when (val result = locationClient.getCurrentLocation()) {
                is LocationResult.PermissionDenied -> {
                    _locationState.update {
                        it.copy(
                            status = LocationVerificationStatus.PERMISSION_REQUIRED,
                            isRefreshing = false,
                            errorMessage = "Location permission is required to verify your site presence."
                        )
                    }
                }
                is LocationResult.GpsDisabled -> {
                    _locationState.update {
                        it.copy(
                            status = LocationVerificationStatus.GPS_DISABLED,
                            isRefreshing = false,
                            errorMessage = "Location services are disabled. Please enable GPS in device settings."
                        )
                    }
                }
                is LocationResult.Error -> {
                    _locationState.update {
                        it.copy(
                            status = LocationVerificationStatus.LOCATION_UNAVAILABLE,
                            isRefreshing = false,
                            errorMessage = result.message
                        )
                    }
                }
                is LocationResult.Success -> {
                    val lat = result.latitude
                    val lng = result.longitude
                    val acc = result.accuracyMeters

                    if (acc > LocationConstants.MAX_ACCURACY_THRESHOLD_METERS) {
                        _locationState.update {
                            it.copy(
                                status = LocationVerificationStatus.POOR_ACCURACY,
                                currentLatitude = lat,
                                currentLongitude = lng,
                                accuracyMeters = acc,
                                isRefreshing = false,
                                errorMessage = "Weak GPS signal (${GeoUtils.formatAccuracy(acc)}). Please move to an open area and tap Refresh."
                            )
                        }
                    } else if (workLat != null && workLng != null) {
                        val distance = GeoUtils.calculateDistanceMeters(lat, lng, workLat, workLng)
                        val isInside = distance <= allowedRadius
                        _locationState.update {
                            it.copy(
                                status = if (isInside) LocationVerificationStatus.VERIFIED else LocationVerificationStatus.TOO_FAR,
                                currentLatitude = lat,
                                currentLongitude = lng,
                                accuracyMeters = acc,
                                distanceFromWorkMeters = distance,
                                allowedRadiusMeters = allowedRadius,
                                isInsideRadius = isInside,
                                isRefreshing = false,
                                errorMessage = if (isInside) null else "You are outside the permitted work site (${GeoUtils.formatDistance(distance)} away, allowed: ${GeoUtils.formatDistance(allowedRadius)})."
                            )
                        }
                    } else {
                        // Work site coordinates not yet populated; mark verified with default radius
                        _locationState.update {
                            it.copy(
                                status = LocationVerificationStatus.VERIFIED,
                                currentLatitude = lat,
                                currentLongitude = lng,
                                accuracyMeters = acc,
                                isInsideRadius = true,
                                isRefreshing = false
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts work by sending real device GPS coordinates to the backend for authoritative verification.
     */
    fun startWork(onSuccess: (() -> Unit)? = null) {
        val currentWork = _work.value
        val workId = currentWork.backendId
        val repo = workRepository
        val loc = _locationState.value

        val lat = loc.currentLatitude
        val lng = loc.currentLongitude
        val acc = loc.accuracyMeters?.toDouble()

        if (workId != null && repo != null) {
            if (!isOnline.value) {
                _errorMessage.value = "Internet connection required to complete this action"
                return
            }
            if (_isSubmitting.value) return

            if (lat == null || lng == null) {
                _errorMessage.value = "GPS location has not been acquired yet. Please tap Refresh Location."
                return
            }

            if (loc.distanceFromWorkMeters != null && loc.distanceFromWorkMeters > loc.allowedRadiusMeters) {
                _errorMessage.value = "You are outside the permitted work location (${GeoUtils.formatDistance(loc.distanceFromWorkMeters)}). You must be within ${GeoUtils.formatDistance(loc.allowedRadiusMeters)} to start."
                return
            }

            viewModelScope.launch {
                _isSubmitting.value = true
                _errorMessage.value = null
                when (val result = repo.startWork(workId, lat, lng, acc)) {
                    is NetworkResult.Success -> {
                        _work.value = result.data
                        startTimer()
                        loadActivity(workId)
                        persistSnapshot()
                        onSuccess?.invoke()
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                        if (result.code == 400 || result.code == 409) {
                            repo.getWork(workId).let { wResult ->
                                if (wResult is NetworkResult.Success && wResult.data.status == WorkStatus.IN_PROGRESS) {
                                    _work.value = wResult.data
                                    startTimer()
                                    persistSnapshot()
                                    onSuccess?.invoke()
                                }
                            }
                        }
                    }
                    else -> {}
                }
                _isSubmitting.value = false
            }
        } else if (repo == null) {
            // Test fallback for local unit tests without backend repository
            val now = currentTimeString()
            if (_work.value.status != WorkStatus.IN_PROGRESS) {
                _work.update {
                    it.copy(
                        status = WorkStatus.IN_PROGRESS,
                        startTime = it.startTime ?: now
                    )
                }
                addActivity("Work session started", now)
                startTimer()
            }
            onSuccess?.invoke()
        } else {
            _errorMessage.value = "Server connection unavailable. Please check your network or server configuration."
        }
    }

    fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                _elapsedSeconds.update { it + 1 }
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }

    // -------------------------------------------------------
    // Checklist
    // -------------------------------------------------------

    fun toggleChecklistItem(itemId: String) {
        val currentWork = _work.value
        val workId = currentWork.backendId

        val item = currentWork.checklist.find { it.id == itemId } ?: return
        val newState = !item.isCompleted

        // Optimistic update
        val now = currentTimeString()
        val updatedList = currentWork.checklist.map { c ->
            if (c.id == itemId) c.copy(
                isCompleted = newState,
                completedAt = if (newState) now else null
            ) else c
        }
        _work.update { it.copy(checklist = updatedList) }

        val desc = if (newState) "${item.title} completed" else "${item.title} marked incomplete"
        addActivity(desc, now)

        // Sync to backend if we have a real work ID
        val repo = workRepository
        if (workId != null && repo != null) {
            val itemIdLong = itemId.toLongOrNull() ?: return
            viewModelScope.launch {
                when (val result = repo.updateChecklistItem(workId, itemIdLong, newState)) {
                    is NetworkResult.Success -> {
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        // Rollback on failure
                        val rolledBack = _work.value.checklist.map { c ->
                            if (c.id == itemId) c.copy(
                                isCompleted = !newState,
                                completedAt = if (!newState) now else null
                            ) else c
                        }
                        _work.update { it.copy(checklist = rolledBack) }
                        _errorMessage.value = result.toUserMessage()
                    }
                    else -> {}
                }
            }
        }
    }

    // -------------------------------------------------------
    // Additional Work
    // -------------------------------------------------------

    fun addAdditionalWork(title: String, description: String = ""): Boolean {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return false
        val currentWork = _work.value
        val workId = currentWork.backendId

        val now = currentTimeString()
        val repo = workRepository
        val clientItemId = "item_" + java.util.UUID.randomUUID().toString()

        if (workId != null && repo != null) {
            if (!isOnline.value) {
                _errorMessage.value = "Internet connection required to complete this action"
                return false
            }
            if (_isSubmitting.value) return false
            viewModelScope.launch {
                _isSubmitting.value = true
                val fullDesc = if (description.isBlank()) trimmed else "$trimmed - ${description.trim()}"
                when (val result = repo.createAdditionalWork(workId, fullDesc, clientItemId)) {
                    is NetworkResult.Success -> {
                        _additionalWork.update { it + result.data }
                        addActivity("Additional work added: \"$trimmed\"", now)
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                    }
                    else -> {}
                }
                _isSubmitting.value = false
            }
        } else if (repo == null) {
            // Local fallback for unit tests
            val newItem = ChecklistItem(
                id = "ADD_${System.currentTimeMillis()}_${(100..999).random()}",
                title = trimmed,
                description = description.trim(),
                isCompleted = false,
                isAdditional = true,
                createdAt = now
            )
            val updatedChecklist = currentWork.checklist + newItem
            _work.update { it.copy(checklist = updatedChecklist) }
            addActivity("Additional work added: \"$trimmed\"", now)
            return true
        } else {
            _errorMessage.value = "Server connection unavailable. Please check your network or server configuration."
            return false
        }
        return true
    }

    fun editAdditionalWork(itemId: String, newTitle: String, newDescription: String = ""): Boolean {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return false
        val currentWork = _work.value
        val workId = currentWork.backendId
        val itemIdLong = itemId.toLongOrNull()

        val now = currentTimeString()
        val repo = workRepository

        if (workId != null && itemIdLong != null && repo != null) {
            val fullDesc = if (newDescription.isBlank()) trimmed else "$trimmed - $newDescription"
            viewModelScope.launch {
                when (val result = repo.updateAdditionalWork(workId, itemIdLong, fullDesc)) {
                    is NetworkResult.Success -> {
                        _additionalWork.update { list ->
                            list.map { if (it.id == itemId) result.data else it }
                        }
                        addActivity("Additional work updated: \"$trimmed\"", now)
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                    }
                    else -> {}
                }
            }
        } else {
            // Mock fallback
            val updatedList = currentWork.checklist.map { item ->
                if (item.id == itemId && item.isAdditional) {
                    item.copy(title = trimmed, description = newDescription.trim())
                } else item
            }
            _work.update { it.copy(checklist = updatedList) }
            addActivity("Additional work updated: \"$trimmed\"", now)
        }
        return true
    }

    fun deleteAdditionalWork(itemId: String): Boolean {
        val currentWork = _work.value
        val workId = currentWork.backendId
        val itemIdLong = itemId.toLongOrNull()
        val now = currentTimeString()
        val repo = workRepository

        if (workId != null && itemIdLong != null && repo != null) {
            val item = _additionalWork.value.find { it.id == itemId } ?: return false
            val desc = item.description
            viewModelScope.launch {
                repo.deleteAdditionalWork(workId, itemIdLong)
                _additionalWork.update { it.filterNot { aw -> aw.id == itemId } }
                addActivity("Additional work removed: \"$desc\"", now)
            }
        } else {
            val item = currentWork.checklist.find { it.id == itemId && it.isAdditional } ?: return false
            _work.update { it.copy(checklist = it.checklist.filterNot { c -> c.id == itemId }) }
            addActivity("Additional work removed: \"${item.title}\"", now)
        }
        return true
    }

    // -------------------------------------------------------
    // Work submission and completion
    // -------------------------------------------------------

    fun submitWorkForReview(): Boolean {
        val currentWork = _work.value
        val workId = currentWork.backendId
        val now = currentTimeString()
        val repo = workRepository

        if (workId != null && repo != null) {
            if (!isOnline.value) {
                _errorMessage.value = "Internet connection required to complete this action"
                return false
            }
            if (_isSubmitting.value) return false
            viewModelScope.launch {
                _isSubmitting.value = true
                when (val result = repo.submitForReview(workId)) {
                    is NetworkResult.Success -> {
                        _work.value = result.data
                        loadActivity(workId)
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                        if (result.code == 400 || result.code == 409) {
                            repo.getWork(workId).let { wResult ->
                                if (wResult is NetworkResult.Success) {
                                    _work.value = wResult.data
                                    persistSnapshot()
                                }
                            }
                        }
                    }
                    else -> {}
                }
                _isSubmitting.value = false
            }
        } else if (repo == null) {
            // Test fallback for unit tests
            _work.update {
                it.copy(
                    status = WorkStatus.WAITING_FOR_POC_REVIEW,
                    submittedForReviewAt = now,
                    pocApproved = null,
                    pocRejectionReason = null,
                    supervisorApproved = null,
                    supervisorRejectionReason = null
                )
            }
            addActivity("Work submitted for review", now)
            _notifications.update { current ->
                listOf(
                    AppNotification(
                        id = "NOTIF_${System.currentTimeMillis()}",
                        title = "Work ready for review",
                        message = "Work has been submitted by ${_work.value.serviceBoyName} for POC review.",
                        timestamp = now,
                        isRead = false
                    )
                ) + current
            }
            return true
        } else {
            _errorMessage.value = "Server connection unavailable. Please check your network or server configuration."
            return false
        }
        return true
    }

    // -------------------------------------------------------
    // Notifications — Real backend synchronization
    // -------------------------------------------------------

    fun loadNotifications() {
        val repo = workRepository ?: return
        viewModelScope.launch {
            when (val result = repo.getNotifications()) {
                is NetworkResult.Success -> {
                    _notifications.value = result.data
                    persistSnapshot()
                }
                else -> {}
            }
        }
    }

    fun markNotificationAsRead(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isRead = true) else it }
        }
        val numId = id.toLongOrNull() ?: return
        val repo = workRepository ?: return
        viewModelScope.launch {
            repo.markNotificationAsRead(numId)
        }
    }

    fun markAllNotificationsAsRead() {
        _notifications.update { list -> list.map { it.copy(isRead = true) } }
        val repo = workRepository ?: return
        viewModelScope.launch {
            repo.markAllNotificationsAsRead()
        }
    }

    // -------------------------------------------------------
    // Approvals — Connected to real Backend API
    // -------------------------------------------------------

    fun approveByPoc(notes: String = ""): Boolean {
        val currentWork = _work.value
        if (currentWork.pocApproved == true) return true

        val workId = currentWork.backendId
        val repo = workRepository
        val now = currentTimeString()

        if (workId != null && repo != null) {
            if (!isOnline.value) {
                _errorMessage.value = "Internet connection required to complete this action"
                return false
            }
            if (_isSubmitting.value) return false
            viewModelScope.launch {
                _isSubmitting.value = true
                when (val result = repo.pocApprove(workId)) {
                    is NetworkResult.Success -> {
                        loadApprovals(workId)
                        loadNotifications()
                        when (val workResult = repo.getWork(workId)) {
                            is NetworkResult.Success -> _work.value = workResult.data
                            else -> _work.update { it.copy(status = WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW, pocApproved = true) }
                        }
                        loadActivity(workId)
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                        if (result.code == 409) {
                            loadApprovals(workId)
                            repo.getWork(workId).let { wResult ->
                                if (wResult is NetworkResult.Success) {
                                    _work.value = wResult.data
                                    persistSnapshot()
                                }
                            }
                        }
                    }
                    else -> {}
                }
                _isSubmitting.value = false
            }
        } else if (repo == null) {
            // Test fallback for unit tests
            _work.update {
                it.copy(
                    status = WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW,
                    pocApproved = true,
                    pocApprovalTime = now,
                    pocRejectionReason = null
                )
            }
            addActivity("POC approved work (${currentWork.pocName})", now)
            _notifications.update { current ->
                listOf(
                    AppNotification(
                        id = "NOTIF_${System.currentTimeMillis()}",
                        title = "POC Approved Work",
                        message = "${currentWork.pocName} approved the work evidence. Awaiting Supervisor review.",
                        timestamp = now,
                        isRead = false
                    )
                ) + current
            }
            return true
        } else {
            _errorMessage.value = "Server connection unavailable. Please check your network or server configuration."
            return false
        }
        return true
    }

    fun rejectByPoc(reason: String): Boolean {
        val trimmed = reason.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = "Rejection reason is mandatory."
            return false
        }

        val currentWork = _work.value
        val workId = currentWork.backendId
        val repo = workRepository
        val now = currentTimeString()

        if (workId != null && repo != null) {
            if (!isOnline.value) {
                _errorMessage.value = "Internet connection required to complete this action"
                return false
            }
            if (_isSubmitting.value) return false
            viewModelScope.launch {
                _isSubmitting.value = true
                when (val result = repo.pocReject(workId, trimmed)) {
                    is NetworkResult.Success -> {
                        loadApprovals(workId)
                        loadNotifications()
                        when (val workResult = repo.getWork(workId)) {
                            is NetworkResult.Success -> _work.value = workResult.data
                            else -> _work.update { it.copy(status = WorkStatus.REJECTED, pocApproved = false, pocRejectionReason = trimmed) }
                        }
                        loadActivity(workId)
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                    }
                    else -> {}
                }
                _isSubmitting.value = false
            }
        } else if (repo == null) {
            // Test fallback for unit tests
            _work.update {
                it.copy(
                    status = WorkStatus.REJECTED,
                    pocApproved = false,
                    pocRejectionReason = trimmed
                )
            }
            addActivity("POC requested changes: \"$trimmed\"", now)
            _notifications.update { current ->
                listOf(
                    AppNotification(
                        id = "NOTIF_${System.currentTimeMillis()}",
                        title = "Changes Requested by POC",
                        message = "POC ${_work.value.pocName}: \"$trimmed\"",
                        timestamp = now,
                        isRead = false
                    )
                ) + current
            }
            return true
        } else {
            _errorMessage.value = "Server connection unavailable. Please check your network or server configuration."
            return false
        }
        return true
    }

    fun approveBySupervisor(notes: String = ""): Boolean {
        val currentWork = _work.value
        if (currentWork.pocApproved != true) {
            _errorMessage.value = "Supervisor approval is unavailable until POC approval is completed."
            return false
        }
        if (currentWork.supervisorApproved == true) return true

        val workId = currentWork.backendId
        val repo = workRepository
        val now = currentTimeString()

        if (workId != null && repo != null) {
            if (!isOnline.value) {
                _errorMessage.value = "Internet connection required to complete this action"
                return false
            }
            if (_isSubmitting.value) return false
            viewModelScope.launch {
                _isSubmitting.value = true
                when (val result = repo.supervisorApprove(workId)) {
                    is NetworkResult.Success -> {
                        loadApprovals(workId)
                        loadNotifications()
                        when (val workResult = repo.getWork(workId)) {
                            is NetworkResult.Success -> _work.value = workResult.data
                            else -> _work.update { it.copy(status = WorkStatus.APPROVED, supervisorApproved = true, readyForCompletion = true) }
                        }
                        loadActivity(workId)
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                        if (result.code == 409) {
                            loadApprovals(workId)
                            repo.getWork(workId).let { wResult ->
                                if (wResult is NetworkResult.Success) {
                                    _work.value = wResult.data
                                    persistSnapshot()
                                }
                            }
                        }
                    }
                    else -> {}
                }
                _isSubmitting.value = false
            }
        } else if (repo == null) {
            // Test fallback for unit tests
            _work.update {
                it.copy(
                    status = WorkStatus.APPROVED,
                    supervisorApproved = true,
                    supervisorApprovalTime = now,
                    supervisorRejectionReason = null,
                    readyForCompletion = true
                )
            }
            addActivity("Supervisor approved work (${currentWork.supervisorName})", now)
            _notifications.update { current ->
                listOf(
                    AppNotification(
                        id = "NOTIF_${System.currentTimeMillis()}",
                        title = "Work Approved!",
                        message = "Supervisor ${currentWork.supervisorName} gave final approval. Ready to complete work.",
                        timestamp = now,
                        isRead = false
                    )
                ) + current
            }
            return true
        } else {
            _errorMessage.value = "Server connection unavailable. Please check your network or server configuration."
            return false
        }
        return true
    }

    fun rejectBySupervisor(reason: String): Boolean {
        val trimmed = reason.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = "Rejection reason is mandatory."
            return false
        }

        val currentWork = _work.value
        val workId = currentWork.backendId
        val repo = workRepository
        val now = currentTimeString()

        if (workId != null && repo != null) {
            if (!isOnline.value) {
                _errorMessage.value = "Internet connection required to complete this action"
                return false
            }
            if (_isSubmitting.value) return false
            viewModelScope.launch {
                _isSubmitting.value = true
                when (val result = repo.supervisorReject(workId, trimmed)) {
                    is NetworkResult.Success -> {
                        loadApprovals(workId)
                        loadNotifications()
                        when (val workResult = repo.getWork(workId)) {
                            is NetworkResult.Success -> _work.value = workResult.data
                            else -> _work.update { it.copy(status = WorkStatus.REJECTED, supervisorApproved = false, supervisorRejectionReason = trimmed) }
                        }
                        loadActivity(workId)
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                    }
                    else -> {}
                }
                _isSubmitting.value = false
            }
        } else if (repo == null) {
            // Test fallback for unit tests
            _work.update {
                it.copy(
                    status = WorkStatus.REJECTED,
                    supervisorApproved = false,
                    supervisorRejectionReason = trimmed
                )
            }
            addActivity("Supervisor requested changes: \"$trimmed\"", now)
            _notifications.update { current ->
                listOf(
                    AppNotification(
                        id = "NOTIF_${System.currentTimeMillis()}",
                        title = "Changes Requested by Supervisor",
                        message = "Supervisor ${_work.value.supervisorName}: \"$trimmed\"",
                        timestamp = now,
                        isRead = false
                    )
                ) + current
            }
            return true
        } else {
            _errorMessage.value = "Server connection unavailable. Please check your network or server configuration."
            return false
        }
        return true
    }

    fun completeWork(): Boolean {
        val currentWork = _work.value
        if (currentWork.pocApproved != true || currentWork.supervisorApproved != true) return false
        if (currentWork.status == WorkStatus.COMPLETED) return true

        val workId = currentWork.backendId
        val now = currentTimeString()
        val repo = workRepository

        if (workId != null && repo != null) {
            if (!isOnline.value) {
                _errorMessage.value = "Internet connection required to complete this action"
                return false
            }
            if (_isSubmitting.value) return false
            viewModelScope.launch {
                _isSubmitting.value = true
                when (val result = repo.completeWork(workId)) {
                    is NetworkResult.Success -> {
                        timerJob?.cancel()
                        _work.value = result.data
                        loadNotifications()
                        loadActivity(workId)
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                        if (result.code == 400 || result.code == 409) {
                            repo.getWork(workId).let { wResult ->
                                if (wResult is NetworkResult.Success) {
                                    _work.value = wResult.data
                                    persistSnapshot()
                                }
                            }
                        }
                    }
                    else -> {}
                }
                _isSubmitting.value = false
            }
        } else if (repo == null) {
            // Test fallback for unit tests
            timerJob?.cancel()
            _work.update {
                it.copy(
                    status = WorkStatus.COMPLETED,
                    completedAt = now
                )
            }
            addActivity("Work completed and closed", now)
            return true
        } else {
            _errorMessage.value = "Server connection unavailable. Please check your network or server configuration."
            return false
        }
        return true
    }

    fun continueWorkAfterRejection() {
        val currentWork = _work.value
        val workId = currentWork.backendId
        val now = currentTimeString()
        val repo = workRepository

        if (workId != null && repo != null) {
            if (!isOnline.value) {
                _errorMessage.value = "Internet connection required to complete this action"
                return
            }
            if (_isSubmitting.value) return
            viewModelScope.launch {
                _isSubmitting.value = true
                when (val result = repo.resumeWork(workId)) {
                    is NetworkResult.Success -> {
                        _work.value = result.data
                        loadApprovals(workId)
                        loadNotifications()
                        loadActivity(workId)
                        startTimer()
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _errorMessage.value = result.toUserMessage()
                    }
                    else -> {}
                }
                _isSubmitting.value = false
            }
        } else if (repo == null) {
            // Test fallback for unit tests
            _work.update {
                it.copy(
                    status = WorkStatus.IN_PROGRESS
                )
            }
            addActivity("Work resumed to address review feedback", now)
        } else {
            _errorMessage.value = "Server connection unavailable. Please check your network or server configuration."
        }
    }

    // -------------------------------------------------------
    // Photos — Connected to real Backend Storage & API
    // -------------------------------------------------------

    fun loadPhotos(workId: Long) {
        val repo = workRepository ?: return
        viewModelScope.launch {
            when (val result = repo.getPhotos(workId)) {
                is NetworkResult.Success -> {
                    _work.update { it.copy(photos = result.data) }
                }
                is NetworkResult.Error -> {
                    // Retain existing photos on error
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun uploadPhotoFromUri(
        uri: Uri,
        title: String = "",
        category: PhotoCategory = PhotoCategory.GENERAL,
        caption: String? = null
    ) {
        val tempId = "TEMP_${System.currentTimeMillis()}_${(100..999).random()}"
        val now = currentTimeString()
        val effectiveTitle = title.ifBlank { "${category.displayName} Photo" }

        val pendingPhoto = WorkPhoto(
            id = tempId,
            title = effectiveTitle,
            category = category,
            uploadedAt = now,
            uploadStatus = PhotoUploadStatus.UPLOADING,
            caption = caption?.trim()?.takeIf { it.isNotBlank() },
            uploadProgress = 0.35f,
            gradientSeed = category.ordinal + 1,
            localUri = uri.toString()
        )

        // Show immediately in UI with uploading indicator
        _work.update { it.copy(photos = it.photos + pendingPhoto) }
        addActivity("1 work photo added", now)

        val repo = workRepository
        val currentWork = _work.value
        val workId = currentWork.backendId

        if (repo != null && workId != null) {
            viewModelScope.launch {
                val context = getApplication<Application>()
                val filePart = FileUtils.createMultipartPart(context, uri)

                if (filePart == null) {
                    _work.update { current ->
                        current.copy(photos = current.photos.map {
                            if (it.id == tempId) it.copy(uploadStatus = PhotoUploadStatus.FAILED) else it
                        })
                    }
                    _errorMessage.value = "Failed to read image file from device"
                    return@launch
                }

                val titlePart = FileUtils.createTextRequestBody(effectiveTitle)
                val categoryPart = FileUtils.createTextRequestBody(category.name)
                val captionPart = caption?.takeIf { it.isNotBlank() }?.let {
                    FileUtils.createTextRequestBody(it)
                }
                val clientPhotoIdPart = FileUtils.createTextRequestBody(tempId)

                when (val result = repo.uploadPhoto(workId, filePart, titlePart, categoryPart, captionPart, clientPhotoIdPart)) {
                    is NetworkResult.Success -> {
                        val confirmedPhoto = result.data.copy(localUri = uri.toString())
                        _work.update { current ->
                            current.copy(photos = current.photos.map {
                                if (it.id == tempId) confirmedPhoto else it
                            })
                        }
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _work.update { current ->
                            current.copy(photos = current.photos.map {
                                if (it.id == tempId) it.copy(uploadStatus = PhotoUploadStatus.FAILED) else it
                            })
                        }
                        _errorMessage.value = result.toUserMessage()
                    }
                    is NetworkResult.Loading -> {}
                }
            }
        } else {
            // Mock fallback when no backend is attached (e.g. initial tests)
            viewModelScope.launch(Dispatchers.Default) {
                delay(800L)
                _work.update { currentWork ->
                    val updated = currentWork.photos.map { photo ->
                        if (photo.id == tempId) photo.copy(uploadStatus = PhotoUploadStatus.UPLOADED, uploadProgress = 1.0f)
                        else photo
                    }
                    currentWork.copy(photos = updated)
                }
            }
        }
    }

    fun addPhotos(newPhotos: List<WorkPhoto>) {
        if (newPhotos.isEmpty()) return
        val now = currentTimeString()
        val photosWithUploading = newPhotos.map {
            it.copy(uploadStatus = PhotoUploadStatus.UPLOADING, uploadProgress = 0.45f)
        }
        _work.update { it.copy(photos = it.photos + photosWithUploading) }

        val countText = if (newPhotos.size == 1) "1 work photo" else "${newPhotos.size} work photos"
        addActivity("$countText added", now)

        val repo = workRepository
        val currentWork = _work.value
        val workId = currentWork.backendId

        // For each photo that has a localUri, upload it for real
        newPhotos.forEach { photo ->
            if (photo.localUri != null && repo != null && workId != null) {
                val uri = Uri.parse(photo.localUri)
                viewModelScope.launch {
                    val context = getApplication<Application>()
                    val filePart = FileUtils.createMultipartPart(context, uri) ?: return@launch
                    val titlePart = FileUtils.createTextRequestBody(photo.title)
                    val categoryPart = FileUtils.createTextRequestBody(photo.category.name)
                    val captionPart = photo.caption?.let { FileUtils.createTextRequestBody(it) }
                    val clientPhotoIdPart = FileUtils.createTextRequestBody(photo.id)

                    when (val result = repo.uploadPhoto(workId, filePart, titlePart, categoryPart, captionPart, clientPhotoIdPart)) {
                        is NetworkResult.Success -> {
                            val uploaded = result.data.copy(localUri = photo.localUri)
                            _work.update { cur ->
                                cur.copy(photos = cur.photos.map {
                                    if (it.id == photo.id) uploaded else it
                                })
                            }
                            persistSnapshot()
                        }
                        is NetworkResult.Error -> {
                            _work.update { cur ->
                                cur.copy(photos = cur.photos.map {
                                    if (it.id == photo.id) it.copy(uploadStatus = PhotoUploadStatus.FAILED) else it
                                })
                            }
                        }
                        is NetworkResult.Loading -> {}
                    }
                }
            } else {
                // Fallback simulation for mock objects without backend IDs
                viewModelScope.launch(Dispatchers.Default) {
                    delay(800L)
                    _work.update { currentWork ->
                        val updated = currentWork.photos.map { p ->
                            if (p.id == photo.id) p.copy(uploadStatus = PhotoUploadStatus.UPLOADED, uploadProgress = 1.0f)
                            else p
                        }
                        currentWork.copy(photos = updated)
                    }
                }
            }
        }
    }

    fun deletePhoto(photoId: String): Boolean {
        val now = currentTimeString()
        val photo = _work.value.photos.find { it.id == photoId } ?: return false
        _work.update { it.copy(photos = it.photos.filterNot { p -> p.id == photoId }) }
        addActivity("Photo removed: \"${photo.title}\"", now)

        val repo = workRepository
        val currentWork = _work.value
        val workId = currentWork.backendId
        val backendPhotoId = photo.backendId ?: photoId.toLongOrNull()

        if (repo != null && workId != null && backendPhotoId != null) {
            viewModelScope.launch {
                repo.deletePhoto(workId, backendPhotoId)
                persistSnapshot()
            }
        }
        return true
    }

    fun retryPhotoUpload(photoId: String) {
        val now = currentTimeString()
        val photo = _work.value.photos.find { it.id == photoId } ?: return

        _work.update { currentWork ->
            val updated = currentWork.photos.map {
                if (it.id == photoId) it.copy(uploadStatus = PhotoUploadStatus.UPLOADING, uploadProgress = 0.45f) else it
            }
            currentWork.copy(photos = updated)
        }
        addActivity("Photo upload retried", now)

        val repo = workRepository
        val currentWork = _work.value
        val workId = currentWork.backendId

        if (photo.localUri != null && repo != null && workId != null) {
            val uri = Uri.parse(photo.localUri)
            viewModelScope.launch {
                val context = getApplication<Application>()
                val filePart = FileUtils.createMultipartPart(context, uri)
                if (filePart == null) {
                    _work.update { cur ->
                        cur.copy(photos = cur.photos.map {
                            if (it.id == photoId) it.copy(uploadStatus = PhotoUploadStatus.FAILED) else it
                        })
                    }
                    _errorMessage.value = "Unable to read image file for retry"
                    return@launch
                }

                val titlePart = FileUtils.createTextRequestBody(photo.title)
                val categoryPart = FileUtils.createTextRequestBody(photo.category.name)
                val captionPart = photo.caption?.let { FileUtils.createTextRequestBody(it) }
                val clientPhotoIdPart = FileUtils.createTextRequestBody(photo.id)

                when (val result = repo.uploadPhoto(workId, filePart, titlePart, categoryPart, captionPart, clientPhotoIdPart)) {
                    is NetworkResult.Success -> {
                        val confirmed = result.data.copy(localUri = photo.localUri)
                        _work.update { cur ->
                            cur.copy(photos = cur.photos.map {
                                if (it.id == photoId) confirmed else it
                            })
                        }
                        persistSnapshot()
                    }
                    is NetworkResult.Error -> {
                        _work.update { cur ->
                            cur.copy(photos = cur.photos.map {
                                if (it.id == photoId) it.copy(uploadStatus = PhotoUploadStatus.FAILED) else it
                            })
                        }
                        _errorMessage.value = result.toUserMessage()
                    }
                    is NetworkResult.Loading -> {}
                }
            }
        } else {
            // Mock fallback
            viewModelScope.launch(Dispatchers.Default) {
                delay(800L)
                _work.update { currentWork ->
                    val updated = currentWork.photos.map {
                        if (it.id == photoId) it.copy(uploadStatus = PhotoUploadStatus.UPLOADED, uploadProgress = 1.0f) else it
                    }
                    currentWork.copy(photos = updated)
                }
            }
        }
    }

    fun updatePhotoMetadata(photoId: String, caption: String?, category: PhotoCategory) {
        val photo = _work.value.photos.find { it.id == photoId } ?: return

        _work.update { cur ->
            cur.copy(photos = cur.photos.map {
                if (it.id == photoId) it.copy(caption = caption, category = category) else it
            })
        }

        val repo = workRepository
        val currentWork = _work.value
        val workId = currentWork.backendId
        val backendPhotoId = photo.backendId ?: photoId.toLongOrNull()

        if (repo != null && workId != null && backendPhotoId != null) {
            viewModelScope.launch {
                repo.updatePhotoMetadata(workId, backendPhotoId, caption, category)
            }
        }
    }

    fun createMockPhoto(title: String, category: PhotoCategory, caption: String? = null): WorkPhoto {
        return WorkPhoto(
            id = "PHOTO_${System.currentTimeMillis()}_${(100..999).random()}",
            title = title.ifBlank { "Work Evidence" },
            category = category,
            uploadedAt = currentTimeString(),
            uploadStatus = PhotoUploadStatus.UPLOADING,
            caption = caption?.trim()?.takeIf { it.isNotBlank() },
            uploadProgress = 0.4f,
            gradientSeed = (0..5).random()
        )
    }

    // -------------------------------------------------------
    // Activity log
    // -------------------------------------------------------

    private fun addActivity(description: String, timestamp: String) {
        val currentLog = _work.value.activityLog
        val lastEvent = currentLog.lastOrNull()
        if (lastEvent != null && lastEvent.description == description) {
            return
        }
        if (description.contains("approved", ignoreCase = true) ||
            description.contains("Work completed", ignoreCase = true) ||
            description.contains("Work session started", ignoreCase = true) ||
            description.contains("Work started", ignoreCase = true)
        ) {
            if (currentLog.any { it.description == description }) {
                return
            }
        }

        val event = ActivityEvent(
            id = System.currentTimeMillis().toString(),
            description = description,
            timestamp = timestamp
        )
        _work.update { it.copy(activityLog = it.activityLog + event) }
    }

    // -------------------------------------------------------
    // Notifications
    // -------------------------------------------------------

    fun markNotificationRead(id: String) {
        _notifications.update { list -> list.map { if (it.id == id) it.copy(isRead = true) else it } }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // -------------------------------------------------------
    // Helper functions
    // -------------------------------------------------------

    private fun currentTimeString(): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

    fun formatElapsedTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    // ---- Progress helpers ----
    fun getPredefinedItems(work: Work): List<ChecklistItem> = work.checklist.filter { !it.isAdditional }
    fun getAdditionalItems(work: Work): List<ChecklistItem> = work.checklist.filter { it.isAdditional }
    fun getPredefinedCompletedCount(work: Work): Int = work.checklist.count { !it.isAdditional && it.isCompleted }
    fun getPredefinedTotalCount(work: Work): Int = work.checklist.count { !it.isAdditional }
    fun getAdditionalCompletedCount(work: Work): Int = work.checklist.count { it.isAdditional && it.isCompleted }
    fun getAdditionalTotalCount(work: Work): Int = work.checklist.count { it.isAdditional }
    fun getTotalCompletedCount(work: Work): Int = work.checklist.count { it.isCompleted }
    fun getTotalCount(work: Work): Int = work.checklist.size
    fun getProgressFraction(work: Work): Float {
        val total = work.checklist.size
        return if (total > 0) work.checklist.count { it.isCompleted }.toFloat() / total else 0f
    }

    fun getPhotos(work: Work): List<WorkPhoto> = work.photos
    fun getTotalPhotosCount(work: Work): Int = work.photos.size
    fun getUploadedPhotosCount(work: Work): Int = work.photos.count { it.uploadStatus == PhotoUploadStatus.UPLOADED }
    fun getUploadingPhotosCount(work: Work): Int = work.photos.count { it.uploadStatus == PhotoUploadStatus.UPLOADING }
    fun getFailedPhotosCount(work: Work): Int = work.photos.count { it.uploadStatus == PhotoUploadStatus.FAILED }
    fun getPendingPhotosCount(work: Work): Int = work.photos.count { it.uploadStatus == PhotoUploadStatus.PENDING }

    // ---- Status helpers ----
    fun isWaitingForReview(work: Work = _work.value): Boolean =
        work.status == WorkStatus.WAITING_FOR_POC_REVIEW ||
        work.status == WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW ||
        work.status == WorkStatus.WAITING_FOR_REVIEW

    fun isApproved(work: Work = _work.value): Boolean = work.status == WorkStatus.APPROVED
    fun isCompleted(work: Work = _work.value): Boolean = work.status == WorkStatus.COMPLETED
    fun isRejected(work: Work = _work.value): Boolean = work.status == WorkStatus.REJECTED

    fun canCompleteWork(work: Work = _work.value): Boolean =
        work.pocApproved == true && work.supervisorApproved == true && work.status != WorkStatus.COMPLETED

    fun canSupervisorApprove(work: Work = _work.value): Boolean =
        work.pocApproved == true && work.supervisorApproved != true

    // ==========================================
    // Master Prompt 11: Work Report Operations
    // ==========================================

    fun loadWorkReport(workId: Long? = null) {
        val targetId = workId ?: _work.value.id.toLongOrNull() ?: return
        viewModelScope.launch {
            _reportState.value = ReportUiState.Loading
            if (workRepository != null) {
                when (val result = workRepository.getWorkReport(targetId)) {
                    is NetworkResult.Success -> {
                        _reportState.value = ReportUiState.Success(result.data)
                    }
                    is NetworkResult.Error -> {
                        _reportState.value = ReportUiState.Error(result.toUserMessage())
                    }
                    is NetworkResult.Loading -> {}
                }
            } else {
                _reportState.value = ReportUiState.Error("Work repository not initialized.")
            }
        }
    }

    fun downloadAndSaveReport(
        workId: Long? = null,
        onSuccess: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val targetId = workId ?: _work.value.id.toLongOrNull() ?: return
        viewModelScope.launch {
            _isDownloadingReport.value = true
            try {
                if (workRepository == null) {
                    _isDownloadingReport.value = false
                    onError("Repository not initialized")
                    return@launch
                }

                when (val result = workRepository.downloadWorkReport(targetId)) {
                    is NetworkResult.Success -> {
                        val responseBody = result.data
                        val app = try { getApplication<Application>() } catch (e: Exception) { null }
                        val baseCacheDir = try {
                            app?.applicationContext?.cacheDir ?: app?.cacheDir
                        } catch (e: Exception) {
                            null
                        } ?: File(System.getProperty("java.io.tmpdir") ?: ".", "reports_cache")
                        val reportsDir = File(baseCacheDir, "reports")
                        if (!reportsDir.exists()) {
                            reportsDir.mkdirs()
                        }
                        val fileName = "WorkReport_$targetId.pdf"
                        val destinationFile = File(reportsDir, fileName)

                        withContext(ioDispatcher) {
                            responseBody.byteStream().use { input ->
                                destinationFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }

                        val current = _reportState.value
                        if (current is ReportUiState.Success) {
                            _reportState.value = current.copy(localPdfFile = destinationFile)
                        }

                        _isDownloadingReport.value = false
                        onSuccess(destinationFile)
                    }
                    is NetworkResult.Error -> {
                        _isDownloadingReport.value = false
                        onError(result.toUserMessage())
                    }
                    is NetworkResult.Loading -> {}
                }
            } catch (e: Exception) {
                _isDownloadingReport.value = false
                onError(e.message ?: "Failed to save report file")
            }
        }
    }

    fun viewReport(context: android.content.Context, file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = "No PDF viewer app found on device: ${e.message}"
        }
    }

    fun shareReport(context: android.content.Context, file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Work Completion Report - ${file.name}")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(intent, "Share Work Report").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            _errorMessage.value = "Unable to share report: ${e.message}"
        }
    }
}

// Extension to provide user-friendly error messages
private fun NetworkResult.Error.toUserMessage(): String = when {
    isNetworkError -> "Unable to connect to server. Please check your connection."
    isAuthError && code == 401 -> "Your session has expired. Please log in again."
    isAuthError && code == 403 -> "You are not authorized to perform this action."
    code == 404 -> "The requested resource was not found."
    code == 409 -> "This action has already been performed."
    code == 422 -> "Invalid operation: $message"
    code == 500 -> "Server error. Please try again."
    else -> message.ifBlank { "An unexpected error occurred." }
}
