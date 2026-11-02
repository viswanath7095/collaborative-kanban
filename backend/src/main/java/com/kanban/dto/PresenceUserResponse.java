package com.kanban.dto;

import java.util.UUID;

public record PresenceUserResponse(
        UUID userId,
        String name) {
}