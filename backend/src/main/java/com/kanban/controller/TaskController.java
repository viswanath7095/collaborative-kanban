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

import com.kanban.dto.CreateTaskRequest;
import com.kanban.dto.TaskResponse;
import com.kanban.dto.UpdateTaskRequest;
import com.kanban.service.TaskService;
import org.springframework.web.bind.annotation.PatchMapping;
import com.kanban.dto.MoveTaskRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tenants/{tenantId}/boards/{boardId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(Authentication auth,
                               @PathVariable UUID organizationId,
                               @PathVariable UUID tenantId,
                               @PathVariable UUID boardId,
                               @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create((UUID) auth.getPrincipal(), organizationId, tenantId, boardId, request);
    }

    @GetMapping
    public List<TaskResponse> list(Authentication auth,
                                   @PathVariable UUID organizationId,
                                   @PathVariable UUID tenantId,
                                   @PathVariable UUID boardId) {
        return taskService.list((UUID) auth.getPrincipal(), organizationId, tenantId, boardId);
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(Authentication auth,
                            @PathVariable UUID organizationId,
                            @PathVariable UUID tenantId,
                            @PathVariable UUID boardId,
                            @PathVariable UUID taskId) {
        return taskService.get((UUID) auth.getPrincipal(), organizationId, tenantId, boardId, taskId);
    }

    @PutMapping("/{taskId}")
    public TaskResponse update(Authentication auth,
                               @PathVariable UUID organizationId,
                               @PathVariable UUID tenantId,
                               @PathVariable UUID boardId,
                               @PathVariable UUID taskId,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update((UUID) auth.getPrincipal(), organizationId, tenantId, boardId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth,
                       @PathVariable UUID organizationId,
                       @PathVariable UUID tenantId,
                       @PathVariable UUID boardId,
                       @PathVariable UUID taskId) {
        taskService.delete((UUID) auth.getPrincipal(), organizationId, tenantId, boardId, taskId);
    }
    @PatchMapping("/{taskId}/move")
    public TaskResponse move(Authentication auth,
                             @PathVariable UUID organizationId,
                             @PathVariable UUID tenantId,
                             @PathVariable UUID boardId,
                             @PathVariable UUID taskId,
                             @Valid @RequestBody MoveTaskRequest request) {
        return taskService.move((UUID) auth.getPrincipal(), organizationId, tenantId, boardId, taskId, request);
    }
}