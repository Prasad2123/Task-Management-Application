package com.fieldservice.repository;

import com.fieldservice.entity.WorkEntity;
import com.fieldservice.entity.WorkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkRepository extends JpaRepository<WorkEntity, Long> {

    // Service Boy sees his own work
    List<WorkEntity> findByServiceBoyId(Long serviceBoyId);

    // POC sees work assigned to them
    List<WorkEntity> findByPocId(Long pocId);

    // Supervisor sees work assigned to them
    List<WorkEntity> findBySupervisorId(Long supervisorId);

    // Check work belongs to a specific service boy
    boolean existsByIdAndServiceBoyId(Long workId, Long serviceBoyId);

    // Check work belongs to a specific poc
    boolean existsByIdAndPocId(Long workId, Long pocId);

    // Check work belongs to a specific supervisor
    boolean existsByIdAndSupervisorId(Long workId, Long supervisorId);

    // Active (non-completed) work for service boy
    List<WorkEntity> findByServiceBoyIdAndStatusNot(Long serviceBoyId, WorkStatus status);
}
