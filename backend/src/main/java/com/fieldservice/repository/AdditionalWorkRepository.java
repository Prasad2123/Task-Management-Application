package com.fieldservice.repository;

import com.fieldservice.entity.AdditionalWorkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdditionalWorkRepository extends JpaRepository<AdditionalWorkEntity, Long> {
    List<AdditionalWorkEntity> findByWorkIdOrderByCreatedAtAsc(Long workId);
    Optional<AdditionalWorkEntity> findByIdAndWorkId(Long id, Long workId);
    Optional<AdditionalWorkEntity> findByWorkIdAndClientItemId(Long workId, String clientItemId);
}
