package com.example.taskmanagementapplication.home.viewmodel

import androidx.lifecycle.ViewModel
import com.example.taskmanagementapplication.core.mock.MockWorkRepository
import com.example.taskmanagementapplication.core.model.Work
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {

    private val _work = MutableStateFlow<Work>(MockWorkRepository.demoWork)
    val work: StateFlow<Work> = _work.asStateFlow()

    // Backward compatibility aliases pointing to the unified work
    val serviceBoyWork: StateFlow<Work> = work
    val pocWork: StateFlow<Work> = work
    val supervisorWork: StateFlow<Work> = work
}

