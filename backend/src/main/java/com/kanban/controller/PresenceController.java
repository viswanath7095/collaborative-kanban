package com.kanban.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kanban.dto.PresenceUserResponse;
import com.kanban.service.AccessService;
import com.kanban.service.PresenceService;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tenants/{tenantId}/presence")
public class PresenceController {

    private final PresenceService presenceService;
    private final AccessService accessService;

    public PresenceController(PresenceService presenceService, AccessService accessService) {
        this.presenceService = presenceService;
        this.accessService = accessService;
    }

    @GetMapping
    public List<PresenceUserResponse> online(Authentication auth,
                                             @PathVariable UUID organizationId,
                                             @PathVariable UUID tenantId) {
        accessService.requireAccess((UUID) auth.getPrincipal(), organizationId, tenantId);
        return presenceService.onlineUsers(tenantId);
    }
}