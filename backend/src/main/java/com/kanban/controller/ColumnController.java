package com.kanban.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kanban.dto.ColumnResponse;
import com.kanban.dto.CreateColumnRequest;
import com.kanban.dto.UpdateColumnRequest;
import com.kanban.service.ColumnService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tenants/{tenantId}/boards/{boardId}/columns")
public class ColumnController {

    private final ColumnService columnService;

    public ColumnController(ColumnService columnService) {
        this.columnService = columnService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnResponse create(Authentication auth,
                                 @PathVariable UUID organizationId,
                                 @PathVariable UUID tenantId,
                                 @PathVariable UUID boardId,
                                 @Valid @RequestBody CreateColumnRequest request) {
        return columnService.create((UUID) auth.getPrincipal(), organizationId, tenantId, boardId,
                request.name());
    }

    @PutMapping("/{columnId}")
    public ColumnResponse rename(Authentication auth,
                                 @PathVariable UUID organizationId,
                                 @PathVariable UUID tenantId,
                                 @PathVariable UUID boardId,
                                 @PathVariable UUID columnId,
                                 @Valid @RequestBody UpdateColumnRequest request) {
        return columnService.rename((UUID) auth.getPrincipal(), organizationId, tenantId, boardId,
                columnId, request.name(), request.expectedVersion());
    }

    @DeleteMapping("/{columnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth,
                       @PathVariable UUID organizationId,
                       @PathVariable UUID tenantId,
                       @PathVariable UUID boardId,
                       @PathVariable UUID columnId) {
        columnService.delete((UUID) auth.getPrincipal(), organizationId, tenantId, boardId, columnId);
    }
}