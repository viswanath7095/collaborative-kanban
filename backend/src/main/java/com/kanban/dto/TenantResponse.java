package com.kanban.dto;

import java.util.UUID;

import com.kanban.entity.Role;

public record TenantResponse(
        UUID id,
        UUID organizationId,
        String name,
        Role role) {
}