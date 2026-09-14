package com.fieldservice.service;

import com.fieldservice.dto.StartWorkRequest;
import com.fieldservice.dto.WorkResponse;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.InvalidStateTransitionException;
import com.fieldservice.exception.LocationVerificationException;
import com.fieldservice.exception.ResourceNotFoundException;
import com.fieldservice.repository.WorkRepository;
import com.fieldservice.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkLocationVerificationTest {

    @Mock
    private WorkRepository workRepository;

    @Mock
    private ActivityEventService activityEventService;

    @InjectMocks
    private WorkService workService;

    private UserEntity serviceBoy;
    private UserEntity otherServiceBoy;
    private UserEntity poc;
    private WorkEntity work;

    // Work location: Andheri East, Mumbai
    private final double WORK_LAT = 19.1136;
    private final double WORK_LNG = 72.8697;
    private final double ALLOWED_RADIUS = 150.0; // 150 meters

    @BeforeEach
    void setUp() {
        serviceBoy = UserEntity.builder()
                .id(1L).name("Rahul Patil").email("service@demo.com")
                .role(UserRole.SERVICE_BOY).active(true).build();

        otherServiceBoy = UserEntity.builder()
                .id(99L).name("Other Guy").email("other@demo.com")
                .role(UserRole.SERVICE_BOY).active(true).build();

        poc = UserEntity.builder()
                .id(2L).name("Amit Sharma").email("poc@demo.com")
                .role(UserRole.POC).active(true).build();

        work = WorkEntity.builder()
                .id(100L)
                .title("Pest Control Service")
                .status(WorkStatus.ASSIGNED)
                .serviceBoy(serviceBoy)
                .poc(poc)
                .latitude(WORK_LAT)
                .longitude(WORK_LNG)
                .allowedRadiusMeters(ALLOWED_RADIUS)
                .build();
    }

    @Test
    @DisplayName("1. Valid location inside radius starts work successfully")
    void test1_validLocationStartsWork() {
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));
        when(workRepository.save(any(WorkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ~50m away from site
        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(19.1139)
                .longitude(72.8699)
                .accuracyMeters(12.0)
                .build();

        WorkResponse response = workService.startWork(100L, request, serviceBoy);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getLocationVerified()).isTrue();
        assertThat(response.getDistanceFromWorkMeters()).isLessThanOrEqualTo(ALLOWED_RADIUS);
        assertThat(work.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(work.getStartTime()).isNotNull();

        verify(activityEventService).record(
                eq(work),
                eq(ActivityEventType.WORK_STARTED),
                contains("Work started at verified location"),
                eq(serviceBoy),
                eq(19.1139),
                eq(72.8699),
                eq(12.0)
        );
    }

    @Test
    @DisplayName("2. Location exactly at site coordinates starts work")
    void test2_exactLocationStartsWork() {
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));
        when(workRepository.save(any(WorkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(WORK_LAT)
                .longitude(WORK_LNG)
                .accuracyMeters(5.0)
                .build();

        WorkResponse response = workService.startWork(100L, request, serviceBoy);

        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getDistanceFromWorkMeters()).isLessThan(1.0);
        assertThat(response.getLocationVerified()).isTrue();
    }

    @Test
    @DisplayName("3. Location outside allowed radius is rejected")
    void test3_outsideRadiusRejected() {
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));

        // Location ~1.5 km away in Bandra
        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(19.0596)
                .longitude(72.8295)
                .accuracyMeters(10.0)
                .build();

        assertThatThrownBy(() -> workService.startWork(100L, request, serviceBoy))
                .isInstanceOf(LocationVerificationException.class)
                .hasMessageContaining("outside the permitted work location");

        assertThat(work.getStatus()).isEqualTo(WorkStatus.ASSIGNED);
        assertThat(work.getStartTime()).isNull();
        verify(workRepository, never()).save(any());
        verify(activityEventService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("4. Invalid latitude (> 90 or < -90) rejected")
    void test4_invalidLatitudeRejected() {
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));

        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(95.0)
                .longitude(72.8697)
                .accuracyMeters(10.0)
                .build();

        assertThatThrownBy(() -> workService.startWork(100L, request, serviceBoy))
                .isInstanceOf(LocationVerificationException.class)
                .hasMessageContaining("Invalid GPS coordinates");
    }

    @Test
    @DisplayName("5. Invalid longitude (> 180 or < -180) rejected")
    void test5_invalidLongitudeRejected() {
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));

        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(19.1136)
                .longitude(-190.0)
                .accuracyMeters(10.0)
                .build();

        assertThatThrownBy(() -> workService.startWork(100L, request, serviceBoy))
                .isInstanceOf(LocationVerificationException.class)
                .hasMessageContaining("Invalid GPS coordinates");
    }

    @Test
    @DisplayName("6. Poor GPS accuracy (> 100m) is rejected")
    void test6_poorAccuracyRejected() {
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));

        // Coordinates at site, but accuracy is 150m (poor GPS signal)
        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(WORK_LAT)
                .longitude(WORK_LNG)
                .accuracyMeters(150.0)
                .build();

        assertThatThrownBy(() -> workService.startWork(100L, request, serviceBoy))
                .isInstanceOf(LocationVerificationException.class)
                .hasMessageContaining("GPS accuracy is too low");

        verify(workRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. Unauthorized user cannot start work")
    void test7_unauthorizedUserRejected() {
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));

        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(WORK_LAT)
                .longitude(WORK_LNG)
                .accuracyMeters(10.0)
                .build();

        assertThatThrownBy(() -> workService.startWork(100L, request, otherServiceBoy))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("This work does not belong to you");
    }

    @Test
    @DisplayName("8. Unknown work ID is rejected with ResourceNotFoundException")
    void test8_unknownWorkRejected() {
        when(workRepository.findById(999L)).thenReturn(Optional.empty());

        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(WORK_LAT)
                .longitude(WORK_LNG)
                .accuracyMeters(10.0)
                .build();

        assertThatThrownBy(() -> workService.startWork(999L, request, serviceBoy))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("9. Already started work cannot be started again")
    void test9_alreadyStartedCannotStartAgain() {
        work.setStatus(WorkStatus.IN_PROGRESS);
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));

        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(WORK_LAT)
                .longitude(WORK_LNG)
                .accuracyMeters(10.0)
                .build();

        assertThatThrownBy(() -> workService.startWork(100L, request, serviceBoy))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("IN_PROGRESS");
    }

    @Test
    @DisplayName("10. Completed work cannot be started")
    void test10_completedWorkCannotStart() {
        work.setStatus(WorkStatus.COMPLETED);
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));

        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(WORK_LAT)
                .longitude(WORK_LNG)
                .accuracyMeters(10.0)
                .build();

        assertThatThrownBy(() -> workService.startWork(100L, request, serviceBoy))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("11. Backend generates authoritative start timestamp")
    void test11_backendGeneratesAuthoritativeTimestamp() {
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));
        when(workRepository.save(any(WorkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now().minusSeconds(1);

        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(WORK_LAT)
                .longitude(WORK_LNG)
                .accuracyMeters(5.0)
                .build();

        WorkResponse response = workService.startWork(100L, request, serviceBoy);

        Instant after = Instant.now().plusSeconds(1);

        assertThat(response.getStartTime()).isNotNull();
        assertThat(response.getStartTime()).isAfterOrEqualTo(before);
        assertThat(response.getStartTime()).isBeforeOrEqualTo(after);
        assertThat(work.getStartTime()).isEqualTo(response.getStartTime());
    }

    @Test
    @DisplayName("12. Successful start records timeline event with verified location")
    void test12_timelineEventRecordsVerifiedLocation() {
        when(workRepository.findById(100L)).thenReturn(Optional.of(work));
        when(workRepository.save(any(WorkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StartWorkRequest request = StartWorkRequest.builder()
                .latitude(19.1140)
                .longitude(72.8700)
                .accuracyMeters(8.5)
                .build();

        workService.startWork(100L, request, serviceBoy);

        ArgumentCaptor<Double> latCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lngCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> accCaptor = ArgumentCaptor.forClass(Double.class);

        verify(activityEventService).record(
                eq(work),
                eq(ActivityEventType.WORK_STARTED),
                anyString(),
                eq(serviceBoy),
                latCaptor.capture(),
                lngCaptor.capture(),
                accCaptor.capture()
        );

        assertThat(latCaptor.getValue()).isEqualTo(19.1140);
        assertThat(lngCaptor.getValue()).isEqualTo(72.8700);
        assertThat(accCaptor.getValue()).isEqualTo(8.5);
    }

    @Test
    @DisplayName("13. Haversine distance utility behaves correctly")
    void test13_haversineDistanceCalculation() {
        // Distance between identical coordinates is 0
        double dist0 = GeoUtils.calculateDistanceMeters(19.1136, 72.8697, 19.1136, 72.8697);
        assertThat(dist0).isEqualTo(0.0);

        // Small offset in Mumbai (~111 meters north for 0.001 deg lat)
        double distSmall = GeoUtils.calculateDistanceMeters(19.1136, 72.8697, 19.1146, 72.8697);
        assertThat(distSmall).isBetween(100.0, 120.0);

        // Distance between Mumbai (19.0760, 72.8777) and Pune (18.5204, 73.8567) is approx 120-130 km
        double distMumbaiPune = GeoUtils.calculateDistanceMeters(19.0760, 72.8777, 18.5204, 73.8567);
        assertThat(distMumbaiPune).isBetween(115000.0, 135000.0);
    }
}
