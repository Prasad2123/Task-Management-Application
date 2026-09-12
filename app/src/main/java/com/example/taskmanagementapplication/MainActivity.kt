package com.example.taskmanagementapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.taskmanagementapplication.auth.viewmodel.AuthViewModel
import com.example.taskmanagementapplication.core.navigation.AppNavHost
import com.example.taskmanagementapplication.core.theme.FieldServiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FieldServiceApp()
        }
    }
}

@Composable
fun FieldServiceApp() {
    FieldServiceTheme {
        val navController = rememberNavController()
        val authViewModel: AuthViewModel = viewModel()
        AppNavHost(
            navController = navController,
            authViewModel = authViewModel
        )
    }
}
