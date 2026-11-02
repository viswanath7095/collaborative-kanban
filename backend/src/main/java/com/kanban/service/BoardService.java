package com.kanban.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kanban.config.RealtimeEvents;
import com.kanban.config.TenantContext;
import com.kanban.dto.BoardResponse;
import com.kanban.dto.ColumnResponse;
import com.kanban.dto.UpdateBoardRequest;
import com.kanban.entity.Board;
import com.kanban.entity.BoardColumn;
import com.kanban.repository.BoardColumnRepository;
import com.kanban.repository.BoardRepository;

@Service
public class BoardService {

    private static final String[] DEFAULT_COLUMNS = {"To Do", "In Progress", "Testing", "Done"};

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final AccessService accessService;
    private final TenantContext tenantContext;
    private final ActivityLogService activityLog;
    private final RealtimeEvents events;

    public BoardService(BoardRepository boardRepository,
                        BoardColumnRepository columnRepository,
                        AccessService accessService,
                        TenantContext tenantContext,
                        ActivityLogService activityLog,
                        RealtimeEvents events) {
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.accessService = accessService;
        this.tenantContext = tenantContext;
        this.activityLog = activityLog;
        this.events = events;
    }

    @Transactional
    public BoardResponse create(UUID userId, UUID orgId, UUID tenantId,
                                String name, String description) {
        accessService.requireManager(userId, orgId, tenantId);
        tenantContext.apply(tenantId);

        Board board = new Board();
        board.setOrganizationId(orgId);
        board.setTenantId(tenantId);
        board.setName(name.trim());
        board.setDescription(description);
        board.setCreatedBy(userId);
        board = boardRepository.save(board);

        List<BoardColumn> columns = new ArrayList<>();
        for (int i = 0; i < DEFAULT_COLUMNS.length; i++) {
            BoardColumn c = new BoardColumn();
            c.setBoardId(board.getId());
            c.setTenantId(tenantId);
            c.setName(DEFAULT_COLUMNS[i]);
            c.setPosition(String.format("%07d", (i + 1) * 1000));
            columns.add(c);
        }
        columns = columnRepository.saveAll(columns);

        BoardResponse response = toResponse(board, columns);
        activityLog.log(tenantId, userId, "BOARD", board.getId(), "CREATED",
                Map.of("name", board.getName()));
        events.publish(tenantId, board.getId(), "board:created", userId, Map.of("board", response));
        return response;
    }

    @Transactional(readOnly = true)
    public List<BoardResponse> list(UUID userId, UUID orgId, UUID tenantId) {
        accessService.requireAccess(userId, orgId, tenantId);
        tenantContext.apply(tenantId);

        List<Board> boards = boardRepository.findByTenantId(tenantId);
        Map<UUID, List<BoardColumn>> columnsByBoard = columnRepository
                .findByBoardIdIn(boards.stream().map(Board::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(BoardColumn::getBoardId));

        return boards.stream()
                .sorted(Comparator.comparing(Board::getName, String.CASE_INSENSITIVE_ORDER))
                .map(b -> toResponse(b, columnsByBoard.getOrDefault(b.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardResponse get(UUID userId, UUID orgId, UUID tenantId, UUID boardId) {
        accessService.requireAccess(userId, orgId, tenantId);
        tenantContext.apply(tenantId);

        Board board = requireBoard(tenantId, boardId);
        return toResponse(board, columnRepository.findByBoardIdIn(List.of(boardId)));
    }

    @Transactional
    public BoardResponse update(UUID userId, UUID orgId, UUID tenantId, UUID boardId,
                                UpdateBoardRequest r) {
        accessService.requireManager(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        Board board = requireBoard(tenantId, boardId);

        if (board.getVersion() != r.expectedVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Board was modified by someone else. Current version is " + board.getVersion());
        }

        List<String> changed = new ArrayList<>();
        String newName = r.name().trim();
        if (!board.getName().equals(newName)) {
            board.setName(newName);
            changed.add("name");
        }
        if (!Objects.equals(board.getDescription(), r.description())) {
            board.setDescription(r.description());
            changed.add("description");
        }

        List<BoardColumn> columns = columnRepository.findByBoardIdIn(List.of(boardId));
        if (changed.isEmpty()) {
            return toResponse(board, columns);
        }

        board = boardRepository.saveAndFlush(board);
        BoardResponse response = toResponse(board, columns);

        activityLog.log(tenantId, userId, "BOARD", boardId, "UPDATED",
                Map.of("changed", String.join(",", changed), "version", board.getVersion()));
        events.publish(tenantId, boardId, "board:updated", userId, Map.of("board", response));
        return response;
    }

    @Transactional
    public void delete(UUID userId, UUID orgId, UUID tenantId, UUID boardId) {
        accessService.requireManager(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        Board board = requireBoard(tenantId, boardId);

        String name = board.getName();
        boardRepository.delete(board);
        boardRepository.flush(); // columns and tasks are removed by the DB (ON DELETE CASCADE)

        activityLog.log(tenantId, userId, "BOARD", boardId, "DELETED", Map.of("name", name));
        events.publish(tenantId, boardId, "board:deleted", userId, Map.of("boardId", boardId));
    }

    private Board requireBoard(UUID tenantId, UUID boardId) {
        return boardRepository.findByIdAndTenantId(boardId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Board not found"));
    }

    private BoardResponse toResponse(Board board, List<BoardColumn> columns) {
        List<ColumnResponse> cols = columns.stream()
                .sorted(Comparator.comparing(BoardColumn::getPosition))
                .map(ColumnResponse::from)
                .toList();
        return new BoardResponse(board.getId(), board.getTenantId(),
                board.getName(), board.getDescription(), board.getVersion(), cols);
    }
}