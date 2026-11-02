package com.kanban.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class ActivityLogService {

    @PersistenceContext
    private EntityManager entityManager;

    /** Must run inside the caller's transaction (and after TenantContext.apply). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(UUID tenantId, UUID userId, String entityType, UUID entityId,
                    String action, Map<String, Object> details) {
        entityManager.createNativeQuery(
                "insert into activity_logs (tenant_id, user_id, entity_type, entity_id, action, details) "
                + "values (?1, ?2, ?3, ?4, ?5, cast(?6 as jsonb))")
                .setParameter(1, tenantId)
                .setParameter(2, userId)
                .setParameter(3, entityType)
                .setParameter(4, entityId)
                .setParameter(5, action)
                .setParameter(6, toJson(details))
                .executeUpdate();
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(escape(v.toString())).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}