package com.fieldservice.repository;

import com.fieldservice.entity.ActivityEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityEventRepository extends JpaRepository<ActivityEventEntity, Long> {
    List<ActivityEventEntity> findByWorkIdOrderByEventTimestampAsc(Long workId);
}
