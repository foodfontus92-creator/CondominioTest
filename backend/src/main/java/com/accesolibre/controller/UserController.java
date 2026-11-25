package com.accesolibre.controller;

import com.accesolibre.dto.user.CreateUserRequest;
import com.accesolibre.dto.user.ResidentsSummaryResponse;
import com.accesolibre.dto.user.UpdateUserRequest;
import com.accesolibre.dto.user.UserResponse;
import com.accesolibre.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/condos/{condoId}/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(@PathVariable UUID condoId) {
        return ResponseEntity.ok(userService.listUsers(condoId));
    }

    @GetMapping("/summary")
    public ResponseEntity<ResidentsSummaryResponse> summary(@PathVariable UUID condoId) {
        return ResponseEntity.ok(userService.getResidentsSummary(condoId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> get(@PathVariable UUID condoId, @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUser(condoId, userId));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @PathVariable UUID condoId,
            @Valid @RequestBody CreateUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(condoId, request));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> update(
            @PathVariable UUID condoId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(condoId, userId, request));
    }
}
