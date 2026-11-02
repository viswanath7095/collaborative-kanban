package com.kanban.dto;

import java.time.Instant;
import java.util.UUID;

import com.kanban.entity.Priority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotNull UUID columnId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        Priority priority,
        Instant dueDate,
        UUID assignedTo) {
}