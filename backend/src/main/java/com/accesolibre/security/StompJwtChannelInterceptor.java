package com.accesolibre.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern PUBLIC_CALL_TOPIC =
            Pattern.compile("^/topic/public/intercom/call/[0-9a-fA-F\\-]{36}$");
    private static final Pattern UNIT_INTERCOM_TOPIC =
            Pattern.compile("^/topic/condo/([0-9a-fA-F\\-]{36})/unit/([0-9a-fA-F\\-]{36})/intercom$");
    private static final Pattern CONDO_INTERCOM_TOPIC =
            Pattern.compile("^/topic/condo/([0-9a-fA-F\\-]{36})/intercom$");
    private static final Pattern CONDO_DEVICES_TOPIC =
            Pattern.compile("^/topic/condo/([0-9a-fA-F\\-]{36})/devices$");

    private final JwtService jwtService;

    public StompJwtChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            authenticateConnect(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(accessor);
        } else if (command == StompCommand.SEND) {
            authorizeSend(accessor);
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null) {
            authHeader = accessor.getFirstNativeHeader("authorization");
        }
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            AuthenticatedUser user = jwtService.parseAccessToken(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.authorities());
            accessor.setUser(authentication);
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new MessagingException("Subscription destination is required");
        }

        // Public visitor status topic for an active door call
        if (PUBLIC_CALL_TOPIC.matcher(destination).matches()) {
            return;
        }

        AuthenticatedUser user = extractAuthenticatedUser(accessor);
        if (user == null) {
            throw new MessagingException("Authentication required to subscribe to " + destination);
        }

        Matcher unitMatcher = UNIT_INTERCOM_TOPIC.matcher(destination);
        if (unitMatcher.matches()) {
            UUID condoId = UUID.fromString(unitMatcher.group(1));
            UUID unitId = UUID.fromString(unitMatcher.group(2));
            if (user.isSuperAdmin()) {
                return;
            }
            if (!condoId.equals(user.condominiumId())) {
                throw new MessagingException("Cross-tenant STOMP subscription denied");
            }
            if (user.isResident() && (user.unitId() == null || !unitId.equals(user.unitId()))) {
                throw new MessagingException("Unit-level STOMP subscription denied");
            }
            return;
        }

        Matcher condoMatcher = CONDO_INTERCOM_TOPIC.matcher(destination);
        if (condoMatcher.matches()) {
            UUID condoId = UUID.fromString(condoMatcher.group(1));
            if (user.isSuperAdmin()) {
                return;
            }
            if (user.isCondoAdmin() && condoId.equals(user.condominiumId())) {
                return;
            }
            throw new MessagingException("Administrator role required for condo intercom feed");
        }

        Matcher devicesMatcher = CONDO_DEVICES_TOPIC.matcher(destination);
        if (devicesMatcher.matches()) {
            UUID condoId = UUID.fromString(devicesMatcher.group(1));
            if (user.isSuperAdmin()) {
                return;
            }
            if (user.isCondoAdmin() && condoId.equals(user.condominiumId())) {
                return;
            }
            throw new MessagingException("Administrator role required for live device feed");
        }

        throw new MessagingException("Unauthorized STOMP destination: " + destination);
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination != null && destination.endsWith("/decision")) {
            AuthenticatedUser user = extractAuthenticatedUser(accessor);
            if (user == null) {
                throw new MessagingException("Authentication required to send intercom decisions");
            }
        }
    }

    private AuthenticatedUser extractAuthenticatedUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
