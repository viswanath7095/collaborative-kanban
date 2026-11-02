package com.kanban.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record ActivityResponse(
        UUID id,
        UUID userId,
        String userName,
        String entityType,
        UUID entityId,
        String action,
        @JsonRawValue String details,
        Instant createdAt) {
}