package com.fieldservice.controller;

import com.fieldservice.dto.AdditionalWorkRequest;
import com.fieldservice.dto.AdditionalWorkResponse;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.security.SecurityUtils;
import com.fieldservice.service.AdditionalWorkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/works/{workId}/additional-work")
@RequiredArgsConstructor
public class AdditionalWorkController {

    private final AdditionalWorkService additionalWorkService;

    /** GET /api/works/{workId}/additional-work */
    @GetMapping
    public ResponseEntity<List<AdditionalWorkResponse>> getAll(@PathVariable Long workId) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(additionalWorkService.getAll(workId, currentUser));
    }

    /** POST /api/works/{workId}/additional-work */
    @PostMapping
    public ResponseEntity<AdditionalWorkResponse> create(
            @PathVariable Long workId,
            @Valid @RequestBody AdditionalWorkRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(additionalWorkService.create(workId, request, currentUser));
    }

    /** PUT /api/works/{workId}/additional-work/{id} */
    @PutMapping("/{id}")
    public ResponseEntity<AdditionalWorkResponse> update(
            @PathVariable Long workId,
            @PathVariable Long id,
            @Valid @RequestBody AdditionalWorkRequest request) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(additionalWorkService.update(workId, id, request, currentUser));
    }

    /** DELETE /api/works/{workId}/additional-work/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long workId, @PathVariable Long id) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        additionalWorkService.delete(workId, id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
