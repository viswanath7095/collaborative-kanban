package com.kanban.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kanban.config.RealtimeEvents;
import com.kanban.config.TenantContext;
import com.kanban.dto.ColumnResponse;
import com.kanban.entity.BoardColumn;
import com.kanban.repository.BoardColumnRepository;
import com.kanban.repository.BoardRepository;
import com.kanban.repository.TaskRepository;
import com.kanban.util.Rank;

@Service
public class ColumnService {

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final AccessService accessService;
    private final TenantContext tenantContext;
    private final ActivityLogService activityLog;
    private final RealtimeEvents events;

    public ColumnService(BoardRepository boardRepository,
                         BoardColumnRepository columnRepository,
                         TaskRepository taskRepository,
                         AccessService accessService,
                         TenantContext tenantContext,
                         ActivityLogService activityLog,
                         RealtimeEvents events) {
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.taskRepository = taskRepository;
        this.accessService = accessService;
        this.tenantContext = tenantContext;
        this.activityLog = activityLog;
        this.events = events;
    }

    @Transactional
    public ColumnResponse create(UUID userId, UUID orgId, UUID tenantId, UUID boardId, String name) {
        accessService.requireManager(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        requireBoard(tenantId, boardId);

        String clean = name.trim();
        List<BoardColumn> existing = columnRepository.findByBoardIdIn(List.of(boardId));
        requireUniqueName(existing, clean, null);

        String last = existing.stream()
                .map(BoardColumn::getPosition)
                .max(Comparator.naturalOrder())
                .orElse(null);

        BoardColumn c = new BoardColumn();
        c.setBoardId(boardId);
        c.setTenantId(tenantId);
        c.setName(clean);
        c.setPosition(Rank.after(last));
        c = columnRepository.saveAndFlush(c);

        ColumnResponse response = ColumnResponse.from(c);
        activityLog.log(tenantId, userId, "COLUMN", c.getId(), "CREATED",
                Map.of("name", clean, "boardId", boardId));
        events.publish(tenantId, boardId, "column:created", userId, Map.of("column", response));
        return response;
    }

    @Transactional
    public ColumnResponse rename(UUID userId, UUID orgId, UUID tenantId, UUID boardId,
                                 UUID columnId, String name, int expectedVersion) {
        accessService.requireManager(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        requireBoard(tenantId, boardId);
        BoardColumn c = requireColumn(tenantId, boardId, columnId);

        if (c.getVersion() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Column was modified by someone else. Current version is " + c.getVersion());
        }

        String clean = name.trim();
        if (c.getName().equals(clean)) {
            return ColumnResponse.from(c);
        }
        requireUniqueName(columnRepository.findByBoardIdIn(List.of(boardId)), clean, columnId);

        String oldName = c.getName();
        c.setName(clean);
        c = columnRepository.saveAndFlush(c);

        ColumnResponse response = ColumnResponse.from(c);
        activityLog.log(tenantId, userId, "COLUMN", columnId, "UPDATED",
                Map.of("oldName", oldName, "newName", clean, "version", c.getVersion()));
        events.publish(tenantId, boardId, "column:updated", userId, Map.of("column", response));
        return response;
    }

    @Transactional
    public void delete(UUID userId, UUID orgId, UUID tenantId, UUID boardId, UUID columnId) {
        accessService.requireManager(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        requireBoard(tenantId, boardId);
        BoardColumn c = requireColumn(tenantId, boardId, columnId);

        if (!taskRepository.findByColumnIdOrderByPositionAscIdAsc(columnId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Column still has tasks. Move or delete them first.");
        }

        String name = c.getName();
        columnRepository.delete(c);
        columnRepository.flush();

        activityLog.log(tenantId, userId, "COLUMN", columnId, "DELETED",
                Map.of("name", name, "boardId", boardId));
        events.publish(tenantId, boardId, "column:deleted", userId,
                Map.of("columnId", columnId, "boardId", boardId));
    }

    private void requireBoard(UUID tenantId, UUID boardId) {
        boardRepository.findByIdAndTenantId(boardId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Board not found"));
    }

    private BoardColumn requireColumn(UUID tenantId, UUID boardId, UUID columnId) {
        return columnRepository.findById(columnId)
                .filter(c -> c.getBoardId().equals(boardId) && c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Column not found"));
    }

    private void requireUniqueName(List<BoardColumn> existing, String name, UUID excludeId) {
        boolean duplicate = existing.stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(name) && !c.getId().equals(excludeId));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A column with this name already exists on this board");
        }
    }
}