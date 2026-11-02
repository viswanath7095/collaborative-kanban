package com.kanban.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kanban.dto.MemberResponse;
import com.kanban.entity.Membership;
import com.kanban.entity.Role;
import com.kanban.entity.User;
import com.kanban.repository.MembershipRepository;
import com.kanban.repository.TenantRepository;
import com.kanban.repository.UserRepository;

@Service
public class MemberService {

    private final MembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public MemberService(MembershipRepository membershipRepository,
                         TenantRepository tenantRepository,
                         UserRepository userRepository) {
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MemberResponse add(UUID callerId, UUID orgId, UUID tenantId, String email, Role role) {
        requireManager(callerId, orgId, tenantId);

        if (role == Role.ORG_ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role must be TENANT_ADMIN or MEMBER");
        }

        tenantRepository.findByIdAndOrganizationId(tenantId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No registered user with this email"));

        if (membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User is already a member of this tenant");
        }

        Membership m = new Membership();
        m.setUserId(user.getId());
        m.setOrganizationId(orgId);
        m.setTenantId(tenantId);
        m.setRole(role);
        membershipRepository.save(m);

        return new MemberResponse(user.getId(), user.getName(), user.getEmail(), role);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(UUID callerId, UUID orgId, UUID tenantId) {
        requireAccess(callerId, orgId, tenantId);

        tenantRepository.findByIdAndOrganizationId(tenantId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        List<Membership> memberships = membershipRepository.findByTenantId(tenantId);
        Map<UUID, User> users = userRepository
                .findAllById(memberships.stream().map(Membership::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return memberships.stream()
                .filter(m -> users.containsKey(m.getUserId()))
                .map(m -> {
                    User u = users.get(m.getUserId());
                    return new MemberResponse(u.getId(), u.getName(), u.getEmail(), m.getRole());
                })
                .toList();
    }

    private boolean isOrgAdmin(UUID userId, UUID orgId) {
        return membershipRepository
                .findByUserIdAndOrganizationIdAndTenantIdIsNull(userId, orgId).isPresent();
    }

    private Membership tenantMembership(UUID userId, UUID orgId, UUID tenantId) {
        return membershipRepository.findByUserIdAndTenantId(userId, tenantId)
                .filter(m -> m.getOrganizationId().equals(orgId))
                .orElse(null);
    }

    private void requireManager(UUID userId, UUID orgId, UUID tenantId) {
        if (isOrgAdmin(userId, orgId)) return;
        Membership m = tenantMembership(userId, orgId, tenantId);
        if (m == null || m.getRole() != Role.TENANT_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an organization admin or tenant admin can manage members");
        }
    }

    private void requireAccess(UUID userId, UUID orgId, UUID tenantId) {
        if (isOrgAdmin(userId, orgId)) return;
        if (tenantMembership(userId, orgId, tenantId) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have access to this tenant");
        }
    }
}