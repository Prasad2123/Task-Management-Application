package com.example.taskmanagementapplication.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.example.taskmanagementapplication.work.ui.StartWorkScreen
import com.example.taskmanagementapplication.work.ui.WorkChecklistScreen
import com.example.taskmanagementapplication.work.ui.WorkDetailsScreen
import com.example.taskmanagementapplication.work.ui.WorkInProgressScreen
import com.example.taskmanagementapplication.work.ui.WorkLocationScreen
import com.example.taskmanagementapplication.work.ui.WorkPhotosScreen
import com.example.taskmanagementapplication.work.viewmodel.WorkViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    // Single shared WorkViewModel for the entire application flow across all roles
    val workViewModel: WorkViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
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
                    val destination = when (user.role) {
                        UserRole.SERVICE_BOY -> Routes.SERVICE_HOME
                        UserRole.POC -> Routes.POC_HOME
                        UserRole.SITE_SUPERVISOR -> Routes.SUPERVISOR_HOME
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
                workViewModel = workViewModel
            )
        }

        composable(Routes.POC_HOME) {
            PocHomeScreen(
                authViewModel = authViewModel,
                onReviewWork = { navController.navigate(Routes.POC_REVIEW) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
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
                workViewModel = workViewModel
            )
        }

        composable(Routes.SUPERVISOR_REVIEW) {
            SupervisorReviewScreen(
                onBack = { navController.popBackStack() },
                workViewModel = workViewModel
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
