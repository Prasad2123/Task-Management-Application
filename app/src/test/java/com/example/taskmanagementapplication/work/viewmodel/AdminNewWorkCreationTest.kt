package com.example.taskmanagementapplication.work.viewmodel

import android.app.Application
import com.example.taskmanagementapplication.core.model.ChecklistItem
import com.example.taskmanagementapplication.core.model.Work
import com.example.taskmanagementapplication.core.model.WorkStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying ADMIN — NEW WORK CREATION & MASTER TASK ASSIGNMENT business rules:
 * 1. Admin creates work by selecting tasks from master task list (no free-text typing).
 * 2. Company Name and Location are required.
 * 3. Coordinates are validated (-90..90, -180..180).
 * 4. Google Maps link is stored and validated.
 * 5. Minimum 1 task required ("Select at least one task for this work.").
 * 6. Service Boy availability check: Free vs Busy, prevents assigning busy technician.
 * 7. Snapshot preservation: Checklist items store master_task_id and task_label.
 */
class AdminNewWorkCreationTest {

    private lateinit var viewModel: WorkViewModel

    @Before
    fun setUp() {
        viewModel = WorkViewModel(
            application = Application(),
            initialWork = Work(
                id = "INITIAL_WORK",
                title = "Initial Work",
                companyName = "Initial Co",
                address = "Initial Site",
                serviceBoyName = "None",
                pocName = "None",
                supervisorName = "None",
                status = WorkStatus.COMPLETED, // Completed so technician is free
                scheduledDate = "2026-09-15"
            )
        )
    }

    @Test
    fun testCreateWorkValid_SuccessAndSnapshotPreserved() {
        val selectedTaskIds = listOf(1L, 2L, 5L)

        viewModel.createWorkWithChecklist(
            companyName = "ABC Industrial Services",
            address = "Plot No. 45, Industrial Estate, Andheri East, Mumbai",
            serviceBoyId = 1L,
            pocId = 2L,
            supervisorId = 3L,
            masterTaskIds = selectedTaskIds,
            scheduledDate = "2026-09-15",
            latitude = 19.1136,
            longitude = 72.8697,
            googleMapsLink = "https://www.google.com/maps?q=19.1136,72.8697"
        )

        assertNull(viewModel.errorMessage.value)

        val createdWork = viewModel.work.value
        assertEquals("ABC Industrial Services", createdWork.companyName)
        assertEquals("Plot No. 45, Industrial Estate, Andheri East, Mumbai", createdWork.address)
        assertEquals("ABC Industrial Services Service Work", createdWork.title)
        assertEquals(WorkStatus.NOT_STARTED, createdWork.status)
        assertEquals(19.1136, createdWork.latitude ?: 0.0, 0.0001)
        assertEquals(72.8697, createdWork.longitude ?: 0.0, 0.0001)
        assertEquals("https://www.google.com/maps?q=19.1136,72.8697", createdWork.googleMapsLink)

        // Verify checklist items snapshot
        assertEquals(3, createdWork.checklist.size)
        val taskLabels = createdWork.checklist.map { it.taskLabel }
        assertTrue(taskLabels.contains("General Site Inspection"))
        assertTrue(taskLabels.contains("Pest Control Treatment"))
        assertTrue(taskLabels.contains("Safety Inspection"))

        // None should be marked additional or completed at creation
        assertTrue(createdWork.checklist.none { it.isAdditional })
        assertTrue(createdWork.checklist.none { it.isCompleted })
        assertEquals(listOf(1L, 2L, 5L), createdWork.checklist.mapNotNull { it.masterTaskId })
    }

    @Test
    fun testCreateWork_ZeroTasksBlocked() {
        viewModel.createWorkWithChecklist(
            companyName = "ABC Industrial Services",
            address = "Plot No. 45, Industrial Estate, Mumbai",
            serviceBoyId = 1L,
            pocId = 2L,
            supervisorId = 3L,
            masterTaskIds = emptyList(), // 0 tasks selected
            scheduledDate = "2026-09-15"
        )

        // Must display strict required message
        assertEquals("Select at least one task for this work.", viewModel.errorMessage.value)
    }

    @Test
    fun testCreateWork_InvalidCoordinatesBlocked() {
        // Latitude > 90
        viewModel.createWorkWithChecklist(
            companyName = "ABC Industrial Services",
            address = "Plot No. 45, Mumbai",
            serviceBoyId = 1L,
            pocId = 2L,
            supervisorId = 3L,
            masterTaskIds = listOf(1L),
            latitude = 95.0,
            longitude = 72.8697
        )
        assertEquals(
            "Invalid coordinates. Latitude (-90 to 90), Longitude (-180 to 180).",
            viewModel.errorMessage.value
        )

        // Longitude < -180
        viewModel.createWorkWithChecklist(
            companyName = "ABC Industrial Services",
            address = "Plot No. 45, Mumbai",
            serviceBoyId = 1L,
            pocId = 2L,
            supervisorId = 3L,
            masterTaskIds = listOf(1L),
            latitude = 19.1136,
            longitude = -195.0
        )
        assertEquals(
            "Invalid coordinates. Latitude (-90 to 90), Longitude (-180 to 180).",
            viewModel.errorMessage.value
        )
    }

    @Test
    fun testCreateWork_MissingCompanyOrLocationBlocked() {
        // Blank company
        viewModel.createWorkWithChecklist(
            companyName = "",
            address = "Plot No. 45, Mumbai",
            serviceBoyId = 1L,
            pocId = 2L,
            supervisorId = 3L,
            masterTaskIds = listOf(1L)
        )
        assertEquals("Company Name is required.", viewModel.errorMessage.value)

        // Blank address / location
        viewModel.createWorkWithChecklist(
            companyName = "ABC Co",
            address = "   ",
            serviceBoyId = 1L,
            pocId = 2L,
            supervisorId = 3L,
            masterTaskIds = listOf(1L)
        )
        assertEquals("Location is required.", viewModel.errorMessage.value)
    }

    @Test
    fun testServiceBoyAvailability_FreeVsBusy() {
        val serviceBoyId = 1L

        // Initially Service Boy 1 is FREE
        assertTrue(viewModel.isServiceBoyFree(serviceBoyId))
        assertEquals("FREE", viewModel.getServiceBoyStatus(serviceBoyId))

        // Admin assigns a work to Service Boy 1
        viewModel.createWorkWithChecklist(
            companyName = "Pharma Corp",
            address = "Zone 4, Warehouse B",
            serviceBoyId = serviceBoyId,
            pocId = 2L,
            supervisorId = 3L,
            masterTaskIds = listOf(1L, 2L)
        )
        assertNull(viewModel.errorMessage.value)

        // Now Service Boy 1 is BUSY (assigned to active work)
        assertFalse(viewModel.isServiceBoyFree(serviceBoyId))
        assertEquals("ASSIGNED", viewModel.getServiceBoyStatus(serviceBoyId))

        // Admin attempts to assign another work to the same Service Boy
        viewModel.createWorkWithChecklist(
            companyName = "Another Client",
            address = "Zone 5, Facility C",
            serviceBoyId = serviceBoyId,
            pocId = 2L,
            supervisorId = 3L,
            masterTaskIds = listOf(3L)
        )

        // Must block assignment because technician is busy
        assertEquals(
            "Selected Service Boy is currently engaged in active work. Please select an available Service Boy.",
            viewModel.errorMessage.value
        )
    }

    @Test
    fun testServiceBoyLiveStatusDerivation() {
        val assignedWork = Work(
            id = "W1",
            title = "Work 1",
            companyName = "Co 1",
            address = "Site 1",
            serviceBoyName = "Rahul",
            pocName = "Amit",
            supervisorName = "Suresh",
            status = WorkStatus.NOT_STARTED,
            scheduledDate = "Today"
        )
        assertEquals("ASSIGNED", viewModel.getServiceBoyStatusForWork(assignedWork))

        val inProgressWork = assignedWork.copy(status = WorkStatus.IN_PROGRESS)
        assertEquals("IN_PROGRESS", viewModel.getServiceBoyStatusForWork(inProgressWork))

        val submittedWork = assignedWork.copy(status = WorkStatus.WAITING_FOR_POC_REVIEW)
        assertEquals("SUBMITTED_FOR_REVIEW", viewModel.getServiceBoyStatusForWork(submittedWork))

        val pocApprovedWork = assignedWork.copy(status = WorkStatus.WAITING_FOR_SUPERVISOR_REVIEW)
        assertEquals("POC_APPROVED", viewModel.getServiceBoyStatusForWork(pocApprovedWork))

        val supervisorApprovedWork = assignedWork.copy(status = WorkStatus.APPROVED)
        assertEquals("SUPERVISOR_APPROVED", viewModel.getServiceBoyStatusForWork(supervisorApprovedWork))

        val completedWork = assignedWork.copy(status = WorkStatus.COMPLETED)
        assertEquals("COMPLETED", viewModel.getServiceBoyStatusForWork(completedWork))
    }
}
