package com.fieldservice.controller;

import com.fieldservice.dto.ChecklistItemResponse;
import com.fieldservice.dto.ChecklistUpdateRequest;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.security.SecurityUtils;
import com.fieldservice.service.ChecklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/works/{workId}/checklist")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;

    /** GET /api/works/{workId}/checklist */
    @GetMapping
    public ResponseEntity<List<ChecklistItemResponse>> getChecklist(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(checklistService.getChecklist(workId, currentUser));
    }

    /** PATCH /api/works/{workId}/checklist/{itemId} */
    @PatchMapping("/{itemId}")
    public ResponseEntity<ChecklistItemResponse> updateItem(
            @PathVariable Long workId,
            @PathVariable Long itemId,
            @RequestBody ChecklistUpdateRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(checklistService.updateChecklistItem(workId, itemId, request, currentUser));
    }
}
