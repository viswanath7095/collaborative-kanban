package com.kanban.config;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
public class TenantContext {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Sets the PostgreSQL setting used by the RLS policies.
     * Must be called inside a transaction (is_local = true => scoped to it).
     */
    public void apply(UUID tenantId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("TenantContext.apply() requires an active transaction");
        }
        entityManager
                .createNativeQuery("select set_config('app.current_tenant', ?1, true)")
                .setParameter(1, tenantId.toString())
                .getSingleResult();
    }
}