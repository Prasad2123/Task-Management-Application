package com.example.taskmanagementapplication.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.taskmanagementapplication.admin.ui.AdminDashboardScreen
import com.example.taskmanagementapplication.auth.ui.LoginScreen
import com.example.taskmanagementapplication.auth.viewmodel.AuthViewModel
import com.example.taskmanagementapplication.core.model.UserRole
import com.example.taskmanagementapplication.core.model.WorkStatus
import com.example.taskmanagementapplication.home.ui.PocHomeScreen
import com.example.taskmanagementapplication.home.ui.ServiceBoyHomeScreen
import com.example.taskmanagementapplication.home.ui.SupervisorHomeScreen
import com.example.taskmanagementapplication.profile.ui.ProfileScreen
import com.example.taskmanagementapplication.review.ui.PocReviewScreen
import com.example.taskmanagementapplication.review.ui.SupervisorReviewScreen
import com.example.taskmanagementapplication.splash.SplashScreen
import com.example.taskmanagementapplication.work.ui.ApprovalStatusScreen
import com.example.taskmanagementapplication.work.ui.CompleteWorkScreen
import com.example.taskmanagementapplication.work.ui.WorkCompletedScreen
import com.example.taskmanagementapplication.work.ui.WorkReportScreen
import com.example.taskmanagementapplication.work.ui.StartWorkScreen
import com.example.taskmanagementapplication.work.ui.WorkChecklistScreen
import com.example.taskmanagementapplication.work.ui.WorkDetailsScreen
import com.example.taskmanagementapplication.work.ui.WorkInProgressScreen
import com.example.taskmanagementapplication.work.ui.WorkLocationScreen
import com.example.taskmanagementapplication.work.ui.WorkPhotosScreen
import com.example.taskmanagementapplication.core.ui.NetworkStatusBar
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
fun AppNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    // Single shared WorkViewModel for the entire application flow across all roles
    val workViewModel: WorkViewModel = viewModel()
    val networkStatus by workViewModel.networkStatus.collectAsStateWithLifecycle()
    val lastSyncedText by workViewModel.lastSyncedText.collectAsStateWithLifecycle()

    // Listen to 401 session expiry events
    LaunchedEffect(Unit) {
        com.example.taskmanagementapplication.core.network.AuthEventBus.sessionExpired.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Lifecycle observer for app resume synchronization
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                workViewModel.syncOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NetworkStatusBar(
            networkStatus = networkStatus,
            lastSyncedText = lastSyncedText
        )

        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.weight(1f)
        ) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { user ->
                    workViewModel.loadMyWork()
                    val destination = when (user.role) {
                        UserRole.SERVICE_BOY -> Routes.SERVICE_HOME
                        UserRole.POC -> Routes.POC_HOME
                        UserRole.SITE_SUPERVISOR -> Routes.SUPERVISOR_HOME
                        UserRole.ADMIN -> Routes.ADMIN_DASHBOARD
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SERVICE_HOME) {
            ServiceBoyHomeScreen(
                authViewModel = authViewModel,
                onViewWork = { navController.navigate(Routes.WORK_DETAILS) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                workViewModel = workViewModel
            )
        }

        composable(Routes.WORK_DETAILS) {
            WorkDetailsScreen(
                onBack = { navController.popBackStack() },
                onStartWork = {
                    val status = workViewModel.work.value.status
                    when {
                        workViewModel.isCompleted() -> {
                            navController.navigate(Routes.WORK_COMPLETED)
                        }
                        workViewModel.isApproved() -> {
                            navController.navigate(Routes.COMPLETE_WORK)
                        }
                        workViewModel.isWaitingForReview() || workViewModel.isRejected() -> {
                            navController.navigate(Routes.APPROVAL_STATUS)
                        }
                        status == WorkStatus.IN_PROGRESS || status == WorkStatus.WORK_STARTED -> {
                            navController.navigate(Routes.WORK_IN_PROGRESS)
                        }
                        else -> {
                            navController.navigate(Routes.START_WORK)
                        }
                    }
                },
                onViewLocation = { navController.navigate(Routes.WORK_LOCATION) },
                onViewReport = { navController.navigate(Routes.WORK_REPORT) },
                workViewModel = workViewModel
            )
        }

        composable(Routes.START_WORK) {
            StartWorkScreen(
                onBack = { navController.popBackStack() },
                onWorkStarted = {
                    navController.navigate(Routes.WORK_IN_PROGRESS) {
                        popUpTo(Routes.START_WORK) { inclusive = true }
                    }
                },
                workViewModel = workViewModel
            )
        }

        composable(Routes.WORK_IN_PROGRESS) {
            WorkInProgressScreen(
                onBack = { navController.popBackStack() },
                onViewDetails = { navController.navigate(Routes.WORK_DETAILS) },
                onViewChecklist = { navController.navigate(Routes.WORK_CHECKLIST) },
                onAddPhotos = { navController.navigate(Routes.WORK_PHOTOS) },
                onSubmitForReview = {
                    workViewModel.submitWorkForReview()
                    navController.navigate(Routes.APPROVAL_STATUS)
                },
                onViewApprovalStatus = { navController.navigate(Routes.APPROVAL_STATUS) },
                onProceedToComplete = { navController.navigate(Routes.COMPLETE_WORK) },
                workViewModel = workViewModel
            )
        }

        composable(Routes.WORK_LOCATION) {
            WorkLocationScreen(
                onBack = { navController.popBackStack() },
                workViewModel = workViewModel
            )
        }

        composable(Routes.WORK_CHECKLIST) {
            WorkChecklistScreen(
                onBack = { navController.popBackStack() },
                workViewModel = workViewModel
            )
        }

        composable(Routes.WORK_PHOTOS) {
            WorkPhotosScreen(
                onBack = { navController.popBackStack() },
                onSubmitForReview = {
                    navController.navigate(Routes.APPROVAL_STATUS) {
                        popUpTo(Routes.WORK_IN_PROGRESS)
                    }
                },
                workViewModel = workViewModel
            )
        }

        composable(Routes.SUBMIT_REVIEW) {
            ApprovalStatusScreen(
                onBack = { navController.popBackStack() },
                onContinueWork = {
                    navController.navigate(Routes.WORK_IN_PROGRESS) {
                        popUpTo(Routes.SERVICE_HOME)
                    }
                },
                onProceedToComplete = {
                    navController.navigate(Routes.COMPLETE_WORK)
                },
                workViewModel = workViewModel
            )
        }

        composable(Routes.APPROVAL_STATUS) {
            ApprovalStatusScreen(
                onBack = { navController.popBackStack() },
                onContinueWork = {
                    navController.navigate(Routes.WORK_IN_PROGRESS) {
                        popUpTo(Routes.SERVICE_HOME)
                    }
                },
                onProceedToComplete = {
                    navController.navigate(Routes.COMPLETE_WORK)
                },
                workViewModel = workViewModel
            )
        }

        composable(Routes.COMPLETE_WORK) {
            CompleteWorkScreen(
                onBack = { navController.popBackStack() },
                onWorkCompleted = {
                    navController.navigate(Routes.WORK_COMPLETED) {
                        popUpTo(Routes.SERVICE_HOME)
                    }
                },
                workViewModel = workViewModel
            )
        }

        composable(Routes.WORK_COMPLETED) {
            WorkCompletedScreen(
                onBackToHome = {
                    navController.navigate(Routes.SERVICE_HOME) {
                        popUpTo(Routes.SERVICE_HOME) { inclusive = true }
                    }
                },
                onViewReport = { navController.navigate(Routes.WORK_REPORT) },
                workViewModel = workViewModel
            )
        }

        composable(Routes.WORK_REPORT) {
            WorkReportScreen(
                onBack = { navController.popBackStack() },
                workViewModel = workViewModel
            )
        }

        composable(Routes.POC_HOME) {
            PocHomeScreen(
                authViewModel = authViewModel,
                onReviewWork = { navController.navigate(Routes.POC_REVIEW) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onViewReport = { navController.navigate(Routes.WORK_REPORT) },
                workViewModel = workViewModel
            )
        }

        composable(Routes.POC_REVIEW) {
            PocReviewScreen(
                onBack = { navController.popBackStack() },
                workViewModel = workViewModel
            )
        }

        composable(Routes.SUPERVISOR_HOME) {
            SupervisorHomeScreen(
                authViewModel = authViewModel,
                onReviewWork = { navController.navigate(Routes.SUPERVISOR_REVIEW) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onViewReport = { navController.navigate(Routes.WORK_REPORT) },
                workViewModel = workViewModel
            )
        }

        composable(Routes.SUPERVISOR_REVIEW) {
            SupervisorReviewScreen(
                onBack = { navController.popBackStack() },
                workViewModel = workViewModel
            )
        }

        composable(Routes.ADMIN_DASHBOARD) {
            AdminDashboardScreen(
                authViewModel = authViewModel,
                workViewModel = workViewModel,
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onViewWorkDetails = { work ->
                    workViewModel.selectWork(work)
                    navController.navigate(Routes.WORK_DETAILS)
                },
                onViewPhotos = { work ->
                    workViewModel.selectWork(work)
                    navController.navigate(Routes.WORK_PHOTOS)
                },
                onViewReport = { work ->
                    workViewModel.selectWork(work)
                    navController.navigate(Routes.WORK_REPORT)
                }
            )
        }

        composable(Routes.PROFILE) {
            val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
            ProfileScreen(
                user = currentUser,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
}
