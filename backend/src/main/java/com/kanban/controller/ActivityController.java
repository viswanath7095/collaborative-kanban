package com.kanban.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kanban.dto.ActivityResponse;
import com.kanban.service.ActivityQueryService;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tenants/{tenantId}/activity")
public class ActivityController {

    private final ActivityQueryService activityQueryService;

    public ActivityController(ActivityQueryService activityQueryService) {
        this.activityQueryService = activityQueryService;
    }

    @GetMapping
    public List<ActivityResponse> list(Authentication auth,
                                       @PathVariable UUID organizationId,
                                       @PathVariable UUID tenantId,
                                       @RequestParam(defaultValue = "50") int limit) {
        return activityQueryService.list((UUID) auth.getPrincipal(), organizationId, tenantId, limit);
    }
}