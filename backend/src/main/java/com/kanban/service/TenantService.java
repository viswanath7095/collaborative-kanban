package com.kanban.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kanban.config.TenantContext;
import com.kanban.dto.TenantResponse;
import com.kanban.entity.Membership;
import com.kanban.entity.Role;
import com.kanban.entity.Tenant;
import com.kanban.repository.BoardRepository;
import com.kanban.repository.MembershipRepository;
import com.kanban.repository.TenantRepository;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final BoardRepository boardRepository;
    private final AccessService accessService;
    private final TenantContext tenantContext;
    private final ActivityLogService activityLog;

    public TenantService(TenantRepository tenantRepository,
                         MembershipRepository membershipRepository,
                         BoardRepository boardRepository,
                         AccessService accessService,
                         TenantContext tenantContext,
                         ActivityLogService activityLog) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.boardRepository = boardRepository;
        this.accessService = accessService;
        this.tenantContext = tenantContext;
        this.activityLog = activityLog;
    }

    @Transactional
    public TenantResponse create(UUID userId, UUID organizationId, String name) {
        accessService.requireOrgAdmin(userId, organizationId);

        String cleanName = name.trim();
        if (tenantRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, cleanName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tenant name already exists in this organization");
        }

        Tenant tenant = new Tenant();
        tenant.setOrganizationId(organizationId);
        tenant.setName(cleanName);
        tenant = tenantRepository.save(tenant);

        return new TenantResponse(tenant.getId(), organizationId, tenant.getName(), Role.ORG_ADMIN);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list(UUID userId, UUID organizationId) {
        boolean isOrgAdmin = membershipRepository
                .findByUserIdAndOrganizationIdAndTenantIdIsNull(userId, organizationId)
                .isPresent();

        if (isOrgAdmin) {
            return tenantRepository.findByOrganizationId(organizationId).stream()
                    .map(t -> new TenantResponse(t.getId(), t.getOrganizationId(), t.getName(), Role.ORG_ADMIN))
                    .toList();
        }

        Map<UUID, Role> roleByTenant = new HashMap<>();
        for (Membership m : membershipRepository.findByUserId(userId)) {
            if (organizationId.equals(m.getOrganizationId()) && m.getTenantId() != null) {
                roleByTenant.put(m.getTenantId(), m.getRole());
            }
        }

        if (roleByTenant.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have access to this organization");
        }

        return tenantRepository.findAllById(roleByTenant.keySet()).stream()
                .map(t -> new TenantResponse(t.getId(), t.getOrganizationId(), t.getName(), roleByTenant.get(t.getId())))
                .toList();
    }

    @Transactional
    public TenantResponse update(UUID userId, UUID orgId, UUID tenantId, String name) {
        Role role = accessService.requireManager(userId, orgId, tenantId);
        Tenant tenant = tenantRepository.findByIdAndOrganizationId(tenantId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        String clean = name.trim();
        if (!tenant.getName().equalsIgnoreCase(clean)
                && tenantRepository.existsByOrganizationIdAndNameIgnoreCase(orgId, clean)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tenant name already exists in this organization");
        }

        String oldName = tenant.getName();
        tenant.setName(clean);
        tenant = tenantRepository.saveAndFlush(tenant);

        tenantContext.apply(tenantId);
        activityLog.log(tenantId, userId, "TENANT", tenantId, "UPDATED",
                Map.of("oldName", oldName, "newName", clean));

        return new TenantResponse(tenant.getId(), orgId, tenant.getName(), role);
    }

    @Transactional
    public void delete(UUID userId, UUID orgId, UUID tenantId) {
        accessService.requireOrgAdmin(userId, orgId);
        Tenant tenant = tenantRepository.findByIdAndOrganizationId(tenantId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        tenantContext.apply(tenantId);
        if (!boardRepository.findByTenantId(tenantId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tenant still has boards. Delete them first.");
        }

        tenantRepository.delete(tenant);
        tenantRepository.flush();
    }
}