package com.fieldservice.service;

import com.fieldservice.dto.AdditionalWorkRequest;
import com.fieldservice.dto.AdditionalWorkResponse;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.ResourceNotFoundException;
import com.fieldservice.mapper.EntityMapper;
import com.fieldservice.repository.AdditionalWorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdditionalWorkService {

    private final AdditionalWorkRepository additionalWorkRepository;
    private final WorkService workService;
    private final ActivityEventService activityEventService;

    @Transactional(readOnly = true)
    public List<AdditionalWorkResponse> getAll(Long workId, UserEntity currentUser) {
        WorkEntity work = workService.findWorkById(workId);
        workService.verifyAccess(work, currentUser);
        return additionalWorkRepository.findByWorkIdOrderByCreatedAtAsc(workId)
                .stream().map(EntityMapper::toAdditionalWorkResponse).collect(Collectors.toList());
    }

    @Transactional
    public AdditionalWorkResponse create(Long workId, AdditionalWorkRequest request,
                                          UserEntity currentUser) {
        requireServiceBoy(currentUser);
        WorkEntity work = workService.findWorkById(workId);
        workService.verifyAccess(work, currentUser);

        if (request.getClientItemId() != null && !request.getClientItemId().isBlank()) {
            var existing = additionalWorkRepository.findByWorkIdAndClientItemId(workId, request.getClientItemId().trim());
            if (existing.isPresent()) {
                return EntityMapper.toAdditionalWorkResponse(existing.get());
            }
        }

        AdditionalWorkEntity aw = AdditionalWorkEntity.builder()
                .work(work)
                .description(request.getDescription().trim())
                .clientItemId(request.getClientItemId() != null && !request.getClientItemId().isBlank() ? request.getClientItemId().trim() : null)
                .createdBy(currentUser)
                .build();
        additionalWorkRepository.save(aw);

        activityEventService.record(work, ActivityEventType.ADDITIONAL_WORK_ADDED,
                "Additional work added: \"" + aw.getDescription() + "\"", currentUser);

        return EntityMapper.toAdditionalWorkResponse(aw);
    }

    @Transactional
    public AdditionalWorkResponse update(Long workId, Long id, AdditionalWorkRequest request,
                                          UserEntity currentUser) {
        requireServiceBoy(currentUser);
        WorkEntity work = workService.findWorkById(workId);
        workService.verifyAccess(work, currentUser);

        AdditionalWorkEntity aw = additionalWorkRepository.findByIdAndWorkId(id, workId)
                .orElseThrow(() -> new ResourceNotFoundException("Additional work not found: " + id));

        String old = aw.getDescription();
        aw.setDescription(request.getDescription().trim());
        additionalWorkRepository.save(aw);

        activityEventService.record(work, ActivityEventType.ADDITIONAL_WORK_UPDATED,
                "Additional work updated: \"" + aw.getDescription() + "\"", currentUser);

        return EntityMapper.toAdditionalWorkResponse(aw);
    }

    @Transactional
    public void delete(Long workId, Long id, UserEntity currentUser) {
        requireServiceBoy(currentUser);
        WorkEntity work = workService.findWorkById(workId);
        workService.verifyAccess(work, currentUser);

        AdditionalWorkEntity aw = additionalWorkRepository.findByIdAndWorkId(id, workId)
                .orElseThrow(() -> new ResourceNotFoundException("Additional work not found: " + id));

        String desc = aw.getDescription();
        additionalWorkRepository.delete(aw);

        activityEventService.record(work, ActivityEventType.ADDITIONAL_WORK_DELETED,
                "Additional work removed: \"" + desc + "\"", currentUser);
    }

    private void requireServiceBoy(UserEntity user) {
        if (user.getRole() != UserRole.SERVICE_BOY) {
            throw new AccessDeniedException("Only Service Boy can manage additional work");
        }
    }
}
