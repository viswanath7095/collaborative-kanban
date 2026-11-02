package com.kanban.dto;

import java.time.Instant;
import java.util.UUID;

import com.kanban.entity.Priority;
import com.kanban.entity.Task;

public record TaskResponse(
        UUID id,
        UUID boardId,
        UUID columnId,
        UUID tenantId,
        String title,
        String description,
        Priority priority,
        Instant dueDate,
        UUID assignedTo,
        UUID createdBy,
        String position,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskResponse from(Task t) {
        return new TaskResponse(t.getId(), t.getBoardId(), t.getColumnId(), t.getTenantId(),
                t.getTitle(), t.getDescription(), t.getPriority(), t.getDueDate(),
                t.getAssignedTo(), t.getCreatedBy(), t.getPosition(), t.getVersion(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}