package com.example.taskmanagementapplication.work.viewmodel

import android.app.Application
import com.example.taskmanagementapplication.core.model.ChecklistItem
import com.example.taskmanagementapplication.core.model.MasterTask
import com.example.taskmanagementapplication.core.model.Work
import com.example.taskmanagementapplication.core.model.WorkStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AssignedChecklistAndAdditionalTasksTest {

    private lateinit var viewModel: WorkViewModel

    @Before
    fun setUp() {
        viewModel = WorkViewModel(
            application = Application(),
            initialWork = Work(
                id = "WORK_TEST_101",
                title = "Monthly Pest Control Service",
                companyName = "ABC Industrial Services",
                address = "Plot No. 45, Andheri East, Mumbai",
                serviceBoyName = "Rahul Patil",
                pocName = "Amit Sharma",
                supervisorName = "Suresh Patil",
                status = WorkStatus.IN_PROGRESS,
                scheduledDate = "2026-09-15",
                checklist = listOf(
                    ChecklistItem(
                        id = "CHK_1",
                        title = "General Site Inspection",
                        description = "Standard requirement: General Site Inspection",
                        isCompleted = false,
                        isAdditional = false,
                        masterTaskId = 1L,
                        taskLabel = "General Site Inspection",
                        displayOrder = 1
                    ),
                    ChecklistItem(
                        id = "CHK_2",
                        title = "Equipment Inspection",
                        description = "Standard requirement: Equipment Inspection",
                        isCompleted = false,
                        isAdditional = false,
                        masterTaskId = 3L,
                        taskLabel = "Equipment Inspection",
                        displayOrder = 2
                    ),
                    ChecklistItem(
                        id = "CHK_3",
                        title = "Electrical Inspection",
                        description = "Standard requirement: Electrical Inspection",
                        isCompleted = false,
                        isAdditional = false,
                        masterTaskId = 7L,
                        taskLabel = "Electrical Inspection",
                        displayOrder = 3
                    ),
                    ChecklistItem(
                        id = "CHK_4",
                        title = "HVAC Inspection",
                        description = "Standard requirement: HVAC Inspection",
                        isCompleted = false,
                        isAdditional = false,
                        masterTaskId = 8L,
                        taskLabel = "HVAC Inspection",
                        displayOrder = 4
                    )
                ),
                backendId = 101L
            )
        )
    }

    @Test
    fun testAdminCreatesWorkWithSelectedMasterTasks() {
        // Admin creates work choosing 3 specific master tasks
        val selectedTaskIds = listOf(2L, 5L, 6L)
        viewModel.createWorkWithChecklist(
            title = "Sanitation & Chemical Safety Audit",
            companyName = "Pharma Corp",
            address = "Unit 12, Industrial Zone",
            serviceBoyId = 1L,
            pocId = 2L,
            supervisorId = 3L,
            masterTaskIds = selectedTaskIds,
            scheduledDate = "2026-09-15"
        )

        val createdWork = viewModel.work.value
        assertEquals("Sanitation & Chemical Safety Audit", createdWork.title)
        assertEquals("Pharma Corp", createdWork.companyName)
        assertEquals(3, createdWork.checklist.size)

        // Verify that checklist contains precisely the selected master tasks
        val checklistMasterIds = createdWork.checklist.mapNotNull { it.masterTaskId }
        assertEquals(selectedTaskIds, checklistMasterIds)

        // Verify none of them are marked additional or completed by default
        assertTrue(createdWork.checklist.none { it.isAdditional })
        assertTrue(createdWork.checklist.none { it.isCompleted })
    }

    @Test
    fun testAvailableAdditionalTasksExcludesAssignedTasks() {
        val currentWork = viewModel.work.value
        val assignedTaskIds = currentWork.checklist.filter { !it.isAdditional }.mapNotNull { it.masterTaskId }.toSet()
        assertEquals(setOf(1L, 3L, 7L, 8L), assignedTaskIds)

        val availableForAdditional = viewModel.getAvailableAdditionalTasks(currentWork)

        // Must NOT be empty
        assertTrue(availableForAdditional.isNotEmpty())

        // Must NOT contain any task that is already assigned to the work
        for (task in availableForAdditional) {
            assertFalse("Assigned task ${task.taskLabel} (ID: ${task.id}) must not appear in available additional tasks",
                assignedTaskIds.contains(task.id))
        }

        // e.g. Master task 2L (Pest Control Treatment) was NOT assigned, so it MUST be available
        assertTrue(availableForAdditional.any { it.id == 2L })
    }

    @Test
    fun testSubmissionBlockedWhenAssignedChecklistIsIncomplete() {
        val currentWork = viewModel.work.value
        // Initially 0/4 completed
        assertFalse(viewModel.allAssignedTasksCompleted(currentWork))

        // Complete 3 out of 4 tasks (e.g. 75% completed)
        viewModel.toggleChecklistItem("CHK_1")
        viewModel.toggleChecklistItem("CHK_2")
        viewModel.toggleChecklistItem("CHK_3")

        val partiallyCompletedWork = viewModel.work.value
        val completedCount = partiallyCompletedWork.checklist.count { it.isCompleted }
        assertEquals(3, completedCount)
        assertFalse("3/4 completed must not be considered all completed",
            viewModel.allAssignedTasksCompleted(partiallyCompletedWork))

        // Attempting to submit for review must be rejected
        val submitResult = viewModel.submitWorkForReview()
        assertFalse("Submission must return false when any assigned task is incomplete", submitResult)
        assertEquals("Complete all assigned tasks before submitting.", viewModel.errorMessage.value)
        assertNotEquals(WorkStatus.WAITING_FOR_POC_REVIEW, viewModel.work.value.status)
    }

    @Test
    fun testSubmissionAllowedWhenAllAssignedChecklistCompleted() {
        // Complete all 4 assigned tasks (100% completed)
        viewModel.toggleChecklistItem("CHK_1")
        viewModel.toggleChecklistItem("CHK_2")
        viewModel.toggleChecklistItem("CHK_3")
        viewModel.toggleChecklistItem("CHK_4")

        val fullyCompletedWork = viewModel.work.value
        assertTrue("4/4 completed must satisfy allAssignedTasksCompleted",
            viewModel.allAssignedTasksCompleted(fullyCompletedWork))

        val submitResult = viewModel.submitWorkForReview()
        assertTrue("Submission must succeed when all assigned tasks are checked", submitResult)
        assertEquals(WorkStatus.WAITING_FOR_POC_REVIEW, viewModel.work.value.status)
    }

    @Test
    fun testAdditionalWorkRecordingAndToggling() {
        val masterTask2 = MasterTask(2L, "Pest Control Treatment", "TREATMENT", 2)

        // Verify task 2 is not selected initially
        assertFalse(viewModel.isAdditionalTaskSelected(viewModel.work.value, masterTask2))

        // Add task 2 as additional work
        val addResult = viewModel.addAdditionalMasterTask(masterTask2)
        assertTrue(addResult)

        val workWithAdditional = viewModel.work.value
        assertTrue(viewModel.isAdditionalTaskSelected(workWithAdditional, masterTask2))

        // Verify it is demarcated as additional and completed
        val additionalItem = workWithAdditional.checklist.find { it.isAdditional && it.masterTaskId == 2L }
        assertNotNull(additionalItem)
        assertEquals("Pest Control Treatment", additionalItem?.title)
        assertTrue(additionalItem?.isCompleted == true)
        assertTrue(additionalItem?.isAdditional == true)

        // Toggle task 2 to remove it
        viewModel.toggleAdditionalMasterTask(masterTask2)
        val workAfterToggle = viewModel.work.value
        assertFalse(viewModel.isAdditionalTaskSelected(workAfterToggle, masterTask2))
        assertNull(workAfterToggle.checklist.find { it.isAdditional && it.masterTaskId == 2L })
    }

    @Test
    fun testAssignedChecklistAndAdditionalWorkAreSeparated() {
        // Add additional work
        val masterTask9 = MasterTask(9L, "Equipment Cleaning", "CLEANING", 9)
        viewModel.addAdditionalMasterTask(masterTask9)

        val currentWork = viewModel.work.value
        val predefined = viewModel.getPredefinedItems(currentWork)
        val additional = viewModel.getAdditionalItems(currentWork)

        // 4 assigned tasks
        assertEquals(4, predefined.size)
        assertTrue(predefined.none { it.isAdditional })

        // 1 additional task
        assertEquals(1, additional.size)
        assertTrue(additional.all { it.isAdditional })
        assertEquals("Equipment Cleaning", additional[0].title)

        // Total count matches sum
        assertEquals(5, viewModel.getTotalCount(currentWork))
    }
}
