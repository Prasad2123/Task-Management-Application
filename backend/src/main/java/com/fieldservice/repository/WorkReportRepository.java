package com.fieldservice.repository;

import com.fieldservice.entity.WorkReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkReportRepository extends JpaRepository<WorkReportEntity, Long> {

    Optional<WorkReportEntity> findByWorkId(Long workId);

    Optional<WorkReportEntity> findFirstByWorkIdOrderByGeneratedAtDesc(Long workId);

    boolean existsByWorkId(Long workId);
}
