package com.kanban.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record MoveTaskRequest(
        @NotNull UUID toColumnId,
        UUID prevTaskId,
        UUID nextTaskId,
        @NotNull Integer expectedVersion) {
}