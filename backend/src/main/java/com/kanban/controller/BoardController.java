package com.kanban.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kanban.dto.BoardResponse;
import com.kanban.dto.CreateBoardRequest;
import com.kanban.dto.UpdateBoardRequest;
import com.kanban.service.BoardService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tenants/{tenantId}/boards")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardResponse create(Authentication auth,
                                @PathVariable UUID organizationId,
                                @PathVariable UUID tenantId,
                                @Valid @RequestBody CreateBoardRequest request) {
        return boardService.create((UUID) auth.getPrincipal(), organizationId, tenantId,
                request.name(), request.description());
    }

    @GetMapping
    public List<BoardResponse> list(Authentication auth,
                                    @PathVariable UUID organizationId,
                                    @PathVariable UUID tenantId) {
        return boardService.list((UUID) auth.getPrincipal(), organizationId, tenantId);
    }

    @GetMapping("/{boardId}")
    public BoardResponse get(Authentication auth,
                             @PathVariable UUID organizationId,
                             @PathVariable UUID tenantId,
                             @PathVariable UUID boardId) {
        return boardService.get((UUID) auth.getPrincipal(), organizationId, tenantId, boardId);
    }

    @PutMapping("/{boardId}")
    public BoardResponse update(Authentication auth,
                                @PathVariable UUID organizationId,
                                @PathVariable UUID tenantId,
                                @PathVariable UUID boardId,
                                @Valid @RequestBody UpdateBoardRequest request) {
        return boardService.update((UUID) auth.getPrincipal(), organizationId, tenantId, boardId, request);
    }

    @DeleteMapping("/{boardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth,
                       @PathVariable UUID organizationId,
                       @PathVariable UUID tenantId,
                       @PathVariable UUID boardId) {
        boardService.delete((UUID) auth.getPrincipal(), organizationId, tenantId, boardId);
    }
}