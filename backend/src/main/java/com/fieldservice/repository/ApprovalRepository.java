package com.fieldservice.repository;

import com.fieldservice.entity.ApprovalEntity;
import com.fieldservice.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, Long> {
    Optional<ApprovalEntity> findByWorkIdAndApproverRole(Long workId, UserRole approverRole);
    List<ApprovalEntity> findByWorkIdOrderByCreatedAtAsc(Long workId);
    boolean existsByWorkIdAndApproverRole(Long workId, UserRole approverRole);
}
