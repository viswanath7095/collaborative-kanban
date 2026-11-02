package com.kanban.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kanban.dto.OrganizationResponse;
import com.kanban.entity.Membership;
import com.kanban.entity.Organization;
import com.kanban.entity.Role;
import com.kanban.repository.MembershipRepository;
import com.kanban.repository.OrganizationRepository;
import com.kanban.repository.TenantRepository;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final AccessService accessService;

    public OrganizationService(OrganizationRepository organizationRepository,
                               MembershipRepository membershipRepository,
                               TenantRepository tenantRepository,
                               AccessService accessService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.accessService = accessService;
    }

    @Transactional
    public OrganizationResponse create(UUID userId, String name) {
        Organization org = new Organization();
        org.setName(name.trim());
        org = organizationRepository.save(org);

        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setOrganizationId(org.getId());
        membership.setTenantId(null);
        membership.setRole(Role.ORG_ADMIN);
        membershipRepository.save(membership);

        return new OrganizationResponse(org.getId(), org.getName(), Role.ORG_ADMIN);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listMine(UUID userId) {
        List<Membership> memberships = membershipRepository.findByUserId(userId);

        // One role per organization: ORG_ADMIN wins if the user has it
        Map<UUID, Role> roleByOrg = new HashMap<>();
        for (Membership m : memberships) {
            roleByOrg.merge(m.getOrganizationId(), m.getRole(),
                    (oldRole, newRole) -> oldRole == Role.ORG_ADMIN ? oldRole : newRole);
        }

        return organizationRepository.findAllById(roleByOrg.keySet()).stream()
                .map(o -> new OrganizationResponse(o.getId(), o.getName(), roleByOrg.get(o.getId())))
                .toList();
    }

    @Transactional
    public OrganizationResponse update(UUID userId, UUID orgId, String name) {
        accessService.requireOrgAdmin(userId, orgId);
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        org.setName(name.trim());
        org = organizationRepository.saveAndFlush(org);
        return new OrganizationResponse(org.getId(), org.getName(), Role.ORG_ADMIN);
    }

    @Transactional
    public void delete(UUID userId, UUID orgId) {
        accessService.requireOrgAdmin(userId, orgId);
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        if (!tenantRepository.findByOrganizationId(orgId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Organization still has tenants. Delete them first.");
        }

        organizationRepository.delete(org);
        organizationRepository.flush();
    }
}