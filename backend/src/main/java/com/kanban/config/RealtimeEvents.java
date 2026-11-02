package com.kanban.config;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RealtimeEvents {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEvents.class);

    private final SimpMessagingTemplate template;

    public RealtimeEvents(SimpMessagingTemplate template) {
        this.template = template;
    }

    /** Sends the event to the tenant room AFTER the current DB transaction commits. */
    public void publish(UUID tenantId, UUID boardId, String type, UUID actorId, Map<String, Object> data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("tenantId", tenantId.toString());
        event.put("boardId", boardId == null ? null : boardId.toString());
        event.put("actorId", actorId == null ? null : actorId.toString());
        event.put("occurredAt", Instant.now().toString());
        event.put("data", data);

        Runnable send = () -> {
            try {
                template.convertAndSend("/topic/tenants/" + tenantId, (Object) event);
            } catch (Exception e) {
                log.warn("Failed to broadcast {} for tenant {}", type, tenantId, e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}