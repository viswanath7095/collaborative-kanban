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

import com.kanban.dto.CreateOrganizationRequest;
import com.kanban.dto.OrganizationResponse;
import com.kanban.service.OrganizationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(Authentication auth,
                                       @Valid @RequestBody CreateOrganizationRequest request) {
        return organizationService.create((UUID) auth.getPrincipal(), request.name());
    }

    @GetMapping
    public List<OrganizationResponse> listMine(Authentication auth) {
        return organizationService.listMine((UUID) auth.getPrincipal());
    }

    @PutMapping("/{organizationId}")
    public OrganizationResponse update(Authentication auth,
                                       @PathVariable UUID organizationId,
                                       @Valid @RequestBody CreateOrganizationRequest request) {
        return organizationService.update((UUID) auth.getPrincipal(), organizationId, request.name());
    }

    @DeleteMapping("/{organizationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID organizationId) {
        organizationService.delete((UUID) auth.getPrincipal(), organizationId);
    }
}