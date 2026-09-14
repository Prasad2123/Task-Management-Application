package com.fieldservice.repository;

import com.fieldservice.entity.WorkPhotoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkPhotoRepository extends JpaRepository<WorkPhotoEntity, Long> {
    List<WorkPhotoEntity> findByWorkIdOrderByCreatedAtAsc(Long workId);
    Optional<WorkPhotoEntity> findByIdAndWorkId(Long id, Long workId);
    Optional<WorkPhotoEntity> findByWorkIdAndClientPhotoId(Long workId, String clientPhotoId);
}
