package com.kanban.dto;

import java.util.UUID;

import com.kanban.entity.BoardColumn;

public record ColumnResponse(
        UUID id,
        String name,
        String position,
        int version) {

    public static ColumnResponse from(BoardColumn c) {
        return new ColumnResponse(c.getId(), c.getName(), c.getPosition(), c.getVersion());
    }
}