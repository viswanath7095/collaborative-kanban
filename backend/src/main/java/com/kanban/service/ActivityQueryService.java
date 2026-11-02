package com.kanban.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kanban.config.TenantContext;
import com.kanban.dto.ActivityResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class ActivityQueryService {

    private final AccessService accessService;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public ActivityQueryService(AccessService accessService, TenantContext tenantContext) {
        this.accessService = accessService;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> list(UUID userId, UUID orgId, UUID tenantId, int limit) {
        accessService.requireAccess(userId, orgId, tenantId);
        tenantContext.apply(tenantId);

        int capped = Math.max(1, Math.min(limit, 200));

        List<?> rows = entityManager.createNativeQuery(
                "select a.id, a.user_id, u.name, a.entity_type, a.entity_id, a.action, "
                + "cast(a.details as text), a.created_at "
                + "from activity_logs a left join users u on u.id = a.user_id "
                + "where a.tenant_id = ?1 "
                + "order by a.created_at desc limit ?2")
                .setParameter(1, tenantId)
                .setParameter(2, capped)
                .getResultList();

        return rows.stream().map(r -> {
            Object[] c = (Object[]) r;
            return new ActivityResponse(
                    toUuid(c[0]), toUuid(c[1]), (String) c[2], (String) c[3],
                    toUuid(c[4]), (String) c[5], (String) c[6], toInstant(c[7]));
        }).toList();
    }

    private static UUID toUuid(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static Instant toInstant(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (o instanceof Timestamp ts) {
            return ts.toInstant();
        }
        return Instant.parse(o.toString());
    }
}