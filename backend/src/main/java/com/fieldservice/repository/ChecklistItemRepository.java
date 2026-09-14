package com.fieldservice.repository;

import com.fieldservice.entity.ChecklistItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChecklistItemRepository extends JpaRepository<ChecklistItemEntity, Long> {
    List<ChecklistItemEntity> findByWorkIdOrderByDisplayOrderAscCreatedAtAsc(Long workId);
    Optional<ChecklistItemEntity> findByIdAndWorkId(Long id, Long workId);
}
