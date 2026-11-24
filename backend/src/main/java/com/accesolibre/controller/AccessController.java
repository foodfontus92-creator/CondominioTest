package com.accesolibre.controller;

import com.accesolibre.domain.enums.AccessResult;
import com.accesolibre.dto.access.AccessLogResponse;
import com.accesolibre.dto.access.RemoteOpenRequest;
import com.accesolibre.dto.access.RemoteOpenResponse;
import com.accesolibre.dto.common.PageResponse;
import com.accesolibre.service.AccessControlService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AccessController {

    private final AccessControlService accessControlService;

    public AccessController(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    @PostMapping("/access/open")
    public ResponseEntity<RemoteOpenResponse> remoteOpen(@Valid @RequestBody RemoteOpenRequest request) {
        return ResponseEntity.ok(accessControlService.remoteOpen(request));
    }

    @GetMapping("/condos/{condoId}/access-logs")
    public ResponseEntity<PageResponse<AccessLogResponse>> listLogs(
            @PathVariable UUID condoId,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) AccessResult result,
            @PageableDefault(size = 25) Pageable pageable
    ) {
        return ResponseEntity.ok(accessControlService.listAccessLogs(condoId, deviceId, unitId, result, pageable));
    }
}
