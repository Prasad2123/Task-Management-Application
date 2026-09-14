package com.fieldservice.repository;

import com.fieldservice.entity.UserEntity;
import com.fieldservice.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    java.util.List<UserEntity> findAllByRole(UserRole role);
}
