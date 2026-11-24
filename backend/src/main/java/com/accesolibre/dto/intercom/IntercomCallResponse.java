package com.accesolibre.dto.intercom;

import com.accesolibre.domain.entity.IntercomCall;
import com.accesolibre.domain.enums.IntercomCallStatus;

import java.time.Instant;
import java.util.UUID;

public record IntercomCallResponse(
        UUID id,
        UUID condominiumId,
        UUID deviceId,
        UUID unitId,
        UUID answeredByUserId,
        IntercomCallStatus status,
        String signalingSessionId,
        String visitorName,
        String visitorNote,
        Boolean doorUnlocked,
        Instant startedAt,
        Instant answeredAt,
        Instant endedAt,
        Integer durationSeconds,
        String visitorStatusTopic,
        String unitAlertTopic
) {
    public static IntercomCallResponse from(IntercomCall entity) {
        return new IntercomCallResponse(
                entity.getId(),
                entity.getCondominiumId(),
                entity.getDeviceId(),
                entity.getUnitId(),
                entity.getAnsweredByUserId(),
                entity.getStatus(),
                entity.getSignalingSessionId(),
                entity.getVisitorName(),
                entity.getVisitorNote(),
                entity.getDoorUnlocked(),
                entity.getStartedAt(),
                entity.getAnsweredAt(),
                entity.getEndedAt(),
                entity.getDurationSeconds(),
                "/topic/public/intercom/call/" + entity.getId(),
                "/topic/condo/" + entity.getCondominiumId() + "/unit/" + entity.getUnitId() + "/intercom"
        );
    }
}
