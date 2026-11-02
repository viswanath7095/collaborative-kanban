package com.kanban.config;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import com.kanban.security.JwtService;
import com.kanban.service.AccessService;
import com.kanban.service.PresenceService;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern TENANT_TOPIC =
            Pattern.compile("^/topic/tenants/([0-9a-fA-F-]{36})(/.*)?$");

    private final JwtService jwtService;
    private final AccessService accessService;
    private final PresenceService presenceService;

    public StompAuthInterceptor(JwtService jwtService,
                                AccessService accessService,
                                @Lazy PresenceService presenceService) {
        this.jwtService = jwtService;
        this.accessService = accessService;
        this.presenceService = presenceService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeat etc.
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(accessor);
        } else if (StompCommand.UNSUBSCRIBE.equals(command)) {
            presenceService.leaveSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
        } else if (StompCommand.SEND.equals(command)) {
            throw new MessagingException("Clients are not allowed to publish messages");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing or malformed Authorization header");
        }
        String token = header.substring(7).trim();
        if (!jwtService.isValid(token)) {
            throw new MessagingException("Invalid or expired token");
        }
        UUID userId = jwtService.extractUserId(token);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new MessagingException("Not authenticated");
        }

        String destination = accessor.getDestination();
        Matcher m = destination == null ? null : TENANT_TOPIC.matcher(destination);
        if (m == null || !m.matches()) {
            throw new MessagingException("Subscription to this destination is not allowed");
        }

        UUID tenantId;
        UUID userId;
        try {
            tenantId = UUID.fromString(m.group(1));
            userId = UUID.fromString(user.getName());
        } catch (IllegalArgumentException e) {
            throw new MessagingException("Invalid destination");
        }

        if (!accessService.hasTenantAccess(userId, tenantId)) {
            throw new MessagingException("You do not have access to this tenant");
        }

        presenceService.join(tenantId, userId, accessor.getSessionId(), accessor.getSubscriptionId());
    }
}