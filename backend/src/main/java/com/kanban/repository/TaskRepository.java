package com.kanban.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kanban.entity.Task;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByBoardIdAndTenantId(UUID boardId, UUID tenantId);

    Optional<Task> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Task> findFirstByColumnIdOrderByPositionDescIdDesc(UUID columnId);

    List<Task> findByColumnIdOrderByPositionAscIdAsc(UUID columnId);
}