package com.kanban.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kanban.dto.CreateTenantRequest;
import com.kanban.dto.TenantResponse;
import com.kanban.service.TenantService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(Authentication auth,
                                 @PathVariable UUID organizationId,
                                 @Valid @RequestBody CreateTenantRequest request) {
        return tenantService.create((UUID) auth.getPrincipal(), organizationId, request.name());
    }

    @GetMapping
    public List<TenantResponse> list(Authentication auth,
                                     @PathVariable UUID organizationId) {
        return tenantService.list((UUID) auth.getPrincipal(), organizationId);
    }

    @PutMapping("/{tenantId}")
    public TenantResponse update(Authentication auth,
                                 @PathVariable UUID organizationId,
                                 @PathVariable UUID tenantId,
                                 @Valid @RequestBody CreateTenantRequest request) {
        return tenantService.update((UUID) auth.getPrincipal(), organizationId, tenantId, request.name());
    }

    @DeleteMapping("/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth,
                       @PathVariable UUID organizationId,
                       @PathVariable UUID tenantId) {
        tenantService.delete((UUID) auth.getPrincipal(), organizationId, tenantId);
    }
}