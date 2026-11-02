package com.kanban.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kanban.entity.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    List<Tenant> findByOrganizationId(UUID organizationId);

    Optional<Tenant> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);
}