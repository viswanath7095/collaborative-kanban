package com.kanban.dto;

import java.util.UUID;

import com.kanban.entity.Role;

public record OrganizationResponse(
        UUID id,
        String name,
        Role role) {
}