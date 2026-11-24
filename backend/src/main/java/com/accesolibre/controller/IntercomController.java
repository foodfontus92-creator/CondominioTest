package com.accesolibre.controller;

import com.accesolibre.dto.common.PageResponse;
import com.accesolibre.dto.intercom.InitiateIntercomCallRequest;
import com.accesolibre.dto.intercom.IntercomCallResponse;
import com.accesolibre.dto.intercom.IntercomDecisionRequest;
import com.accesolibre.dto.intercom.PublicIntercomInfoResponse;
import com.accesolibre.exception.UnauthorizedException;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.service.IntercomService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class IntercomController {

    private final IntercomService intercomService;

    public IntercomController(IntercomService intercomService) {
        this.intercomService = intercomService;
    }

    // -------------------------------------------------------------------------
    // Public Visitor Endpoints (Door Static QR Flow - No Resident Account)
    // -------------------------------------------------------------------------

    @GetMapping("/public/intercom/door/{publicQrToken}")
    public ResponseEntity<PublicIntercomInfoResponse> getDoorInfo(@PathVariable String publicQrToken) {
        return ResponseEntity.ok(intercomService.getPublicDoorInfo(publicQrToken));
    }

    @PostMapping("/public/intercom/ring")
    public ResponseEntity<IntercomCallResponse> initiatePublicRing(
            @Valid @RequestBody InitiateIntercomCallRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(intercomService.initiatePublicCall(request));
    }

    @GetMapping("/public/intercom/calls/{callId}")
    public ResponseEntity<IntercomCallResponse> getPublicCallStatus(@PathVariable UUID callId) {
        return ResponseEntity.ok(intercomService.getPublicCallStatus(callId));
    }

    // -------------------------------------------------------------------------
    // Authenticated Resident & Admin REST Endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/condos/{condoId}/intercom-calls")
    public ResponseEntity<PageResponse<IntercomCallResponse>> listCalls(
            @PathVariable UUID condoId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(intercomService.listCalls(condoId, pageable));
    }

    @PostMapping("/condos/{condoId}/intercom-calls/{callId}/answer")
    public ResponseEntity<IntercomCallResponse> answer(@PathVariable UUID condoId, @PathVariable UUID callId) {
        return ResponseEntity.ok(intercomService.handleDecision(condoId, callId, "ANSWER"));
    }

    @PostMapping("/condos/{condoId}/intercom-calls/{callId}/unlock")
    public ResponseEntity<IntercomCallResponse> unlock(@PathVariable UUID condoId, @PathVariable UUID callId) {
        return ResponseEntity.ok(intercomService.handleDecision(condoId, callId, "UNLOCK"));
    }

    @PostMapping("/condos/{condoId}/intercom-calls/{callId}/reject")
    public ResponseEntity<IntercomCallResponse> reject(@PathVariable UUID condoId, @PathVariable UUID callId) {
        return ResponseEntity.ok(intercomService.handleDecision(condoId, callId, "REJECT"));
    }

    @PostMapping("/condos/{condoId}/intercom-calls/{callId}/end")
    public ResponseEntity<IntercomCallResponse> end(@PathVariable UUID condoId, @PathVariable UUID callId) {
        return ResponseEntity.ok(intercomService.handleDecision(condoId, callId, "END"));
    }

    // -------------------------------------------------------------------------
    // Real-Time WebSocket STOMP Handlers (/app/intercom/...)
    // -------------------------------------------------------------------------

    @MessageMapping("/intercom/{callId}/decision")
    public void handleStompDecision(
            @DestinationVariable UUID callId,
            @Valid @Payload IntercomDecisionRequest request,
            Principal principal
    ) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof AuthenticatedUser user) {
            intercomService.handleDecisionFromStomp(callId, request.action(), user);
            return;
        }
        throw new UnauthorizedException("Authenticated STOMP session required for intercom decisions");
    }

    @MessageMapping("/intercom/{callId}/signal")
    public void handleLocalWebRtcSignal(
            @DestinationVariable UUID callId,
            @Payload Map<String, Object> signalPayload
    ) {
        intercomService.relayLocalSignaling(callId, signalPayload);
    }
}
