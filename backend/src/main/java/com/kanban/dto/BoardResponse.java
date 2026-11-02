package com.kanban.dto;

import java.util.List;
import java.util.UUID;

public record BoardResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        int version,
        List<ColumnResponse> columns) {
}