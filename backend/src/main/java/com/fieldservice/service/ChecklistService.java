package com.fieldservice.service;

import com.fieldservice.dto.ChecklistItemResponse;
import com.fieldservice.dto.ChecklistUpdateRequest;
import com.fieldservice.entity.*;
import com.fieldservice.exception.AccessDeniedException;
import com.fieldservice.exception.ResourceNotFoundException;
import com.fieldservice.mapper.EntityMapper;
import com.fieldservice.repository.ChecklistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChecklistService {

    private final ChecklistItemRepository checklistItemRepository;
    private final WorkService workService;
    private final ActivityEventService activityEventService;

    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> getChecklist(Long workId, UserEntity currentUser) {
        WorkEntity work = workService.findWorkById(workId);
        workService.verifyAccess(work, currentUser);
        return checklistItemRepository.findByWorkIdOrderByDisplayOrderAscCreatedAtAsc(workId)
                .stream().map(EntityMapper::toChecklistResponse).collect(Collectors.toList());
    }

    @Transactional
    public ChecklistItemResponse updateChecklistItem(Long workId, Long itemId,
                                                      ChecklistUpdateRequest request,
                                                      UserEntity currentUser) {
        // Only Service Boy can update checklist
        if (currentUser.getRole() != UserRole.SERVICE_BOY) {
            throw new AccessDeniedException("Only Service Boy can update checklist items");
        }

        WorkEntity work = workService.findWorkById(workId);
        workService.verifyAccess(work, currentUser);

        ChecklistItemEntity item = checklistItemRepository.findByIdAndWorkId(itemId, workId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found: " + itemId));

        if (request.getCompleted() != null) {
            item.setCompleted(request.getCompleted());
            if (request.getCompleted()) {
                item.setCompletedAt(Instant.now());
                item.setCompletedBy(currentUser);
            } else {
                item.setCompletedAt(null);
                item.setCompletedBy(null);
            }
        }

        checklistItemRepository.save(item);

        // Log activity
        String desc = item.isCompleted()
                ? item.getTitle() + " completed"
                : item.getTitle() + " marked incomplete";
        activityEventService.record(work, ActivityEventType.CHECKLIST_ITEM_COMPLETED, desc, currentUser);

        return EntityMapper.toChecklistResponse(item);
    }
}
