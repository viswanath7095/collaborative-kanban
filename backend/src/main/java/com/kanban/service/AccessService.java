package com.kanban.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.kanban.entity.Membership;
import com.kanban.entity.Role;
import com.kanban.repository.MembershipRepository;
import com.kanban.repository.TenantRepository;

@Service
public class AccessService {

    private final MembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;

    public AccessService(MembershipRepository membershipRepository,
                         TenantRepository tenantRepository) {
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
    }

    /** Returns the caller's effective role in this tenant, or throws 403/404. */
    public Role requireAccess(UUID userId, UUID orgId, UUID tenantId) {
        boolean orgAdmin = membershipRepository
                .findByUserIdAndOrganizationIdAndTenantIdIsNull(userId, orgId).isPresent();

        if (orgAdmin) {
            tenantRepository.findByIdAndOrganizationId(tenantId, orgId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
            return Role.ORG_ADMIN;
        }

        return membershipRepository.findByUserIdAndTenantId(userId, tenantId)
                .filter(m -> m.getOrganizationId().equals(orgId))
                .map(Membership::getRole)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You do not have access to this tenant"));
    }

    /** Same as requireAccess, but MEMBER is rejected (403). */
    public Role requireManager(UUID userId, UUID orgId, UUID tenantId) {
        Role role = requireAccess(userId, orgId, tenantId);
        if (role == Role.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an organization admin or tenant admin can do this");
        }
        return role;
    }
    /** True if the user may read this tenant (org admin of its organization, or tenant member). */
    public boolean hasTenantAccess(UUID userId, UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> membershipRepository
                        .findByUserIdAndOrganizationIdAndTenantIdIsNull(userId, t.getOrganizationId()).isPresent()
                        || membershipRepository.findByUserIdAndTenantId(userId, tenantId).isPresent())
                .orElse(false);
    }
    
    /** Only a user with an organization-level (ORG_ADMIN) membership passes. */
    public void requireOrgAdmin(UUID userId, UUID orgId) {
        boolean ok = membershipRepository
                .findByUserIdAndOrganizationIdAndTenantIdIsNull(userId, orgId).isPresent();
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an organization admin can do this");
        }
    }
}