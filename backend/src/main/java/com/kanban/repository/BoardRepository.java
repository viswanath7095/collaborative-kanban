package com.kanban.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kanban.entity.Board;

public interface BoardRepository extends JpaRepository<Board, UUID> {

    List<Board> findByTenantId(UUID tenantId);

    Optional<Board> findByIdAndTenantId(UUID id, UUID tenantId);
}