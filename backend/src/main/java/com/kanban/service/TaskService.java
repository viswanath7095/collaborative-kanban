package com.kanban.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kanban.config.RealtimeEvents;
import com.kanban.config.TenantContext;
import com.kanban.dto.CreateTaskRequest;
import com.kanban.dto.MoveTaskRequest;
import com.kanban.dto.TaskResponse;
import com.kanban.dto.UpdateTaskRequest;
import com.kanban.entity.BoardColumn;
import com.kanban.entity.Priority;
import com.kanban.entity.Role;
import com.kanban.entity.Task;
import com.kanban.repository.BoardColumnRepository;
import com.kanban.repository.BoardRepository;
import com.kanban.repository.MembershipRepository;
import com.kanban.repository.TaskRepository;
import com.kanban.util.Rank;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final MembershipRepository membershipRepository;
    private final AccessService accessService;
    private final TenantContext tenantContext;
    private final ActivityLogService activityLog;
    private final RealtimeEvents events;

    @PersistenceContext
    private EntityManager entityManager;

    public TaskService(TaskRepository taskRepository,
                       BoardRepository boardRepository,
                       BoardColumnRepository columnRepository,
                       MembershipRepository membershipRepository,
                       AccessService accessService,
                       TenantContext tenantContext,
                       ActivityLogService activityLog,
                       RealtimeEvents events) {
        this.taskRepository = taskRepository;
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.membershipRepository = membershipRepository;
        this.accessService = accessService;
        this.tenantContext = tenantContext;
        this.activityLog = activityLog;
        this.events = events;
    }

    @Transactional
    public TaskResponse create(UUID userId, UUID orgId, UUID tenantId, UUID boardId,
                               CreateTaskRequest r) {
        accessService.requireAccess(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        requireBoard(tenantId, boardId);
        BoardColumn column = requireColumn(tenantId, boardId, r.columnId());
        validateAssignee(orgId, tenantId, r.assignedTo());

        String last = taskRepository
                .findFirstByColumnIdOrderByPositionDescIdDesc(column.getId())
                .map(Task::getPosition)
                .orElse(null);

        Task t = new Task();
        t.setBoardId(boardId);
        t.setColumnId(column.getId());
        t.setTenantId(tenantId);
        t.setTitle(r.title().trim());
        t.setDescription(r.description());
        t.setPriority(r.priority() == null ? Priority.MEDIUM : r.priority());
        t.setDueDate(r.dueDate());
        t.setAssignedTo(r.assignedTo());
        t.setCreatedBy(userId);
        t.setPosition(Rank.after(last));

        t = taskRepository.saveAndFlush(t);
        entityManager.refresh(t);

        activityLog.log(tenantId, userId, "TASK", t.getId(), "CREATED",
                Map.of("title", t.getTitle(), "column", column.getName()));

        TaskResponse response = TaskResponse.from(t);
        events.publish(tenantId, boardId, "task:created", userId, Map.of("task", response));
        return response;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(UUID userId, UUID orgId, UUID tenantId, UUID boardId) {
        accessService.requireAccess(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        requireBoard(tenantId, boardId);

        return taskRepository.findByBoardIdAndTenantId(boardId, tenantId).stream()
                .sorted(Comparator.comparing(Task::getPosition))
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID userId, UUID orgId, UUID tenantId, UUID boardId, UUID taskId) {
        accessService.requireAccess(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        return TaskResponse.from(requireTask(tenantId, boardId, taskId));
    }

    @Transactional
    public TaskResponse update(UUID userId, UUID orgId, UUID tenantId, UUID boardId,
                               UUID taskId, UpdateTaskRequest r) {
        accessService.requireAccess(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        Task t = requireTask(tenantId, boardId, taskId);

        if (t.getVersion() != r.expectedVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Task was modified by someone else. Current version is " + t.getVersion());
        }

        List<String> changed = new ArrayList<>();

        String newTitle = r.title().trim();
        if (!t.getTitle().equals(newTitle)) {
            t.setTitle(newTitle);
            changed.add("title");
        }
        if (!Objects.equals(t.getDescription(), r.description())) {
            t.setDescription(r.description());
            changed.add("description");
        }
        if (t.getPriority() != r.priority()) {
            t.setPriority(r.priority());
            changed.add("priority");
        }
        if (!Objects.equals(t.getDueDate(), r.dueDate())) {
            t.setDueDate(r.dueDate());
            changed.add("dueDate");
        }
        if (!Objects.equals(t.getAssignedTo(), r.assignedTo())) {
            validateAssignee(orgId, tenantId, r.assignedTo());
            t.setAssignedTo(r.assignedTo());
            changed.add("assignedTo");
        }

        if (changed.isEmpty()) {
            return TaskResponse.from(t);
        }

        t = taskRepository.saveAndFlush(t);
        entityManager.refresh(t);

        activityLog.log(tenantId, userId, "TASK", t.getId(), "UPDATED",
                Map.of("changed", String.join(",", changed), "version", t.getVersion()));

        TaskResponse response = TaskResponse.from(t);
        events.publish(tenantId, boardId, "task:updated", userId, Map.of("task", response));
        return response;
    }

    @Transactional
    public TaskResponse move(UUID userId, UUID orgId, UUID tenantId, UUID boardId,
                             UUID taskId, MoveTaskRequest r) {
        accessService.requireAccess(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        Task t = requireTask(tenantId, boardId, taskId);

        if (t.getVersion() != r.expectedVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Task was modified by someone else. Current version is " + t.getVersion());
        }

        BoardColumn target = requireColumn(tenantId, boardId, r.toColumnId());
        Task prev = requireNeighbor(r.prevTaskId(), t, target, tenantId);
        Task next = requireNeighbor(r.nextTaskId(), t, target, tenantId);
        if (prev != null && next != null && prev.getId().equals(next.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "prevTaskId and nextTaskId must be different tasks");
        }

        String newPosition = computePosition(t, target, prev, next);

        UUID fromColumnId = t.getColumnId();
        if (fromColumnId.equals(target.getId()) && t.getPosition().equals(newPosition)) {
            return TaskResponse.from(t);
        }

        String fromColumnName = columnRepository.findById(fromColumnId)
                .map(BoardColumn::getName).orElse("?");

        t.setColumnId(target.getId());
        t.setPosition(newPosition);
        t = taskRepository.saveAndFlush(t);
        entityManager.refresh(t);

        activityLog.log(tenantId, userId, "TASK", t.getId(), "MOVED",
                Map.of("title", t.getTitle(),
                       "fromColumn", fromColumnName,
                       "toColumn", target.getName(),
                       "version", t.getVersion()));

        TaskResponse response = TaskResponse.from(t);
        events.publish(tenantId, boardId, "task:moved", userId,
                Map.of("task", response, "fromColumnId", fromColumnId));
        return response;
    }

    @Transactional
    public void delete(UUID userId, UUID orgId, UUID tenantId, UUID boardId, UUID taskId) {
        Role role = accessService.requireAccess(userId, orgId, tenantId);
        tenantContext.apply(tenantId);
        Task t = requireTask(tenantId, boardId, taskId);

        if (role == Role.MEMBER && !userId.equals(t.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an admin or the task creator can delete this task");
        }

        String title = t.getTitle();
        taskRepository.delete(t);
        taskRepository.flush();

        activityLog.log(tenantId, userId, "TASK", taskId, "DELETED", Map.of("title", title));
        events.publish(tenantId, boardId, "task:deleted", userId,
                Map.of("taskId", taskId, "boardId", boardId));
    }

    // ---------- helpers ----------

    private Task requireNeighbor(UUID id, Task moving, BoardColumn target, UUID tenantId) {
        if (id == null) {
            return null;
        }
        if (id.equals(moving.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A task cannot be its own neighbour");
        }
        return taskRepository.findByIdAndTenantId(id, tenantId)
                .filter(n -> n.getColumnId().equals(target.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "A neighbouring task is no longer in the target column. Reload the board."));
    }

    private String computePosition(Task moving, BoardColumn target, Task prev, Task next) {
        if (prev == null && next == null) {
            String last = taskRepository.findByColumnIdOrderByPositionAscIdAsc(target.getId()).stream()
                    .filter(x -> !x.getId().equals(moving.getId()))
                    .reduce((a, b) -> b)
                    .map(Task::getPosition)
                    .orElse(null);
            return Rank.after(last);
        }
        if (next == null) {
            return Rank.after(prev.getPosition());
        }

        long lo = prev == null ? 0 : Rank.value(prev.getPosition());
        long hi = Rank.value(next.getPosition());
        if (prev != null && lo >= hi) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Task order changed meanwhile. Reload the board.");
        }
        if (hi - lo >= 2) {
            return Rank.format((lo + hi) / 2);
        }

        // no room between neighbours: renumber the column, then retry
        rebalance(target.getId());
        entityManager.refresh(moving);
        if (prev != null) {
            entityManager.refresh(prev);
        }
        entityManager.refresh(next);
        lo = prev == null ? 0 : Rank.value(prev.getPosition());
        hi = Rank.value(next.getPosition());
        return Rank.format((lo + hi) / 2);
    }

    /** Renumbers a column 1000, 2000, ... with native SQL so task versions are NOT bumped. */
    private void rebalance(UUID columnId) {
        entityManager.flush();
        List<Task> ordered = taskRepository.findByColumnIdOrderByPositionAscIdAsc(columnId);
        long pos = 1000;
        for (Task x : ordered) {
            entityManager.createNativeQuery("update tasks set \"position\" = ?1 where id = ?2")
                    .setParameter(1, Rank.format(pos))
                    .setParameter(2, x.getId())
                    .executeUpdate();
            pos += 1000;
        }
    }

    private void requireBoard(UUID tenantId, UUID boardId) {
        boardRepository.findByIdAndTenantId(boardId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Board not found"));
    }

    private BoardColumn requireColumn(UUID tenantId, UUID boardId, UUID columnId) {
        return columnRepository.findById(columnId)
                .filter(c -> c.getBoardId().equals(boardId) && c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Column does not belong to this board"));
    }

    private Task requireTask(UUID tenantId, UUID boardId, UUID taskId) {
        return taskRepository.findByIdAndTenantId(taskId, tenantId)
                .filter(t -> t.getBoardId().equals(boardId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private void validateAssignee(UUID orgId, UUID tenantId, UUID assignee) {
        if (assignee == null) {
            return;
        }
        boolean ok = membershipRepository.findByUserIdAndTenantId(assignee, tenantId).isPresent()
                || membershipRepository.findByUserIdAndOrganizationIdAndTenantIdIsNull(assignee, orgId).isPresent();
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Assignee must be a member of this tenant");
        }
    }
}