package com.kanban.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.kanban.config.RealtimeEvents;
import com.kanban.dto.PresenceUserResponse;
import com.kanban.repository.UserRepository;

/**
 * In-memory presence: a user is online in a tenant while at least one of their
 * STOMP subscriptions to that tenant room is alive (so several tabs are handled).
 * Single-instance only; a multi-instance deployment would need Redis or similar.
 */
@Service
public class PresenceService {

    private record Sub(String subId, UUID tenantId, UUID userId) {
    }

    // sessionId -> subscriptions of that session
    private final Map<String, List<Sub>> bySession = new HashMap<>();
    // tenantId -> userId -> live connection keys ("sessionId:subId")
    private final Map<UUID, Map<UUID, Set<String>>> online = new HashMap<>();

    private final UserRepository userRepository;
    private final RealtimeEvents events;

    public PresenceService(UserRepository userRepository, RealtimeEvents events) {
        this.userRepository = userRepository;
        this.events = events;
    }

    public void join(UUID tenantId, UUID userId, String sessionId, String subId) {
        boolean becameOnline;
        synchronized (this) {
            bySession.computeIfAbsent(sessionId, k -> new ArrayList<>())
                    .add(new Sub(subId, tenantId, userId));
            Set<String> keys = online
                    .computeIfAbsent(tenantId, k -> new HashMap<>())
                    .computeIfAbsent(userId, k -> new HashSet<>());
            becameOnline = keys.isEmpty();
            keys.add(sessionId + ":" + subId);
        }
        if (becameOnline) {
            announce(tenantId, userId, "presence:online");
        }
    }

    public void leaveSubscription(String sessionId, String subId) {
        Sub gone = null;
        boolean offline = false;
        synchronized (this) {
            List<Sub> subs = bySession.get(sessionId);
            if (subs == null) {
                return;
            }
            Iterator<Sub> it = subs.iterator();
            while (it.hasNext()) {
                Sub s = it.next();
                if (Objects.equals(s.subId(), subId)) {
                    it.remove();
                    gone = s;
                    break;
                }
            }
            if (subs.isEmpty()) {
                bySession.remove(sessionId);
            }
            if (gone != null) {
                offline = removeKey(gone, sessionId);
            }
        }
        if (gone != null && offline) {
            announce(gone.tenantId(), gone.userId(), "presence:offline");
        }
    }

    public void leaveSession(String sessionId) {
        List<Sub> wentOffline = new ArrayList<>();
        synchronized (this) {
            List<Sub> subs = bySession.remove(sessionId);
            if (subs == null) {
                return;
            }
            for (Sub s : subs) {
                if (removeKey(s, sessionId)) {
                    wentOffline.add(s);
                }
            }
        }
        for (Sub s : wentOffline) {
            announce(s.tenantId(), s.userId(), "presence:offline");
        }
    }

    public List<PresenceUserResponse> onlineUsers(UUID tenantId) {
        Set<UUID> ids;
        synchronized (this) {
            Map<UUID, Set<String>> users = online.get(tenantId);
            ids = users == null ? Set.of() : new HashSet<>(users.keySet());
        }
        return userRepository.findAllById(ids).stream()
                .map(u -> new PresenceUserResponse(u.getId(), u.getName()))
                .sorted(Comparator.comparing(PresenceUserResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        leaveSession(event.getSessionId());
    }

    // must be called while holding the lock
    private boolean removeKey(Sub s, String sessionId) {
        Map<UUID, Set<String>> users = online.get(s.tenantId());
        if (users == null) {
            return false;
        }
        Set<String> keys = users.get(s.userId());
        if (keys == null) {
            return false;
        }
        keys.remove(sessionId + ":" + s.subId());
        if (!keys.isEmpty()) {
            return false;
        }
        users.remove(s.userId());
        if (users.isEmpty()) {
            online.remove(s.tenantId());
        }
        return true;
    }

    private void announce(UUID tenantId, UUID userId, String type) {
        userRepository.findById(userId).ifPresent(u ->
                events.publish(tenantId, null, type, userId,
                        Map.of("userId", userId, "name", u.getName())));
    }
}