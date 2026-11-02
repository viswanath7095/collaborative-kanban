package com.kanban.dto;

import java.util.UUID;

import com.kanban.entity.Role;

public record MemberResponse(
        UUID userId,
        String name,
        String email,
        Role role) {
}