package com.accesolibre.controller;

import com.accesolibre.dto.setup.SetupInitializeRequest;
import com.accesolibre.dto.setup.SetupInitializeResponse;
import com.accesolibre.dto.setup.SetupStatusResponse;
import com.accesolibre.service.SetupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final SetupService setupService;
    private final ReentrantLock initializeLock = new ReentrantLock();

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public ResponseEntity<SetupStatusResponse> status() {
        return ResponseEntity.ok(setupService.getStatus());
    }

    @PostMapping("/initialize")
    public ResponseEntity<SetupInitializeResponse> initialize(@Valid @RequestBody SetupInitializeRequest request) {
        initializeLock.lock();
        try {
            SetupInitializeResponse response = setupService.initialize(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            initializeLock.unlock();
        }
    }
}
