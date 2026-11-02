package com.kanban.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kanban.entity.Membership;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByUserId(UUID userId);

    // Organization-level membership (ORG_ADMIN): tenant_id IS NULL
    Optional<Membership> findByUserIdAndOrganizationIdAndTenantIdIsNull(UUID userId, UUID organizationId);

    // Tenant-level membership (TENANT_ADMIN / MEMBER)
    Optional<Membership> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    List<Membership> findByOrganizationId(UUID organizationId);
    
    List<Membership> findByTenantId(UUID tenantId);
}