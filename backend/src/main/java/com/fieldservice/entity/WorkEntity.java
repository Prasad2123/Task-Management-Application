package com.fieldservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "works")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "work_type", length = 100)
    private String workType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String notes;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private WorkStatus status = WorkStatus.ASSIGNED;

    // --- Relationships ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_boy_id", nullable = false)
    private UserEntity serviceBoy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poc_id", nullable = false)
    private UserEntity poc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private UserEntity supervisor;

    // --- Location (embedded for simplicity; extracted to WorkLocationEntity for queries) ---
    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "allowed_radius_meters")
    @Builder.Default
    private Double allowedRadiusMeters = 150.0;

    // --- Lifecycle timestamps (authoritative server-side) ---
    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // --- Audit ---
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
