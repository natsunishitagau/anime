package com.anime.controller;

import com.anime.dto.UserPrincipal;
import com.anime.dto.request.UserSettingsRequest;
import com.anime.dto.response.UserSettingsResponse;
import com.anime.service.UserSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user-settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @GetMapping
    public ResponseEntity<UserSettingsResponse> getSettings(Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        UserSettingsResponse response = userSettingsService.getSettings(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<UserSettingsResponse> updateSettings(
            @Valid @RequestBody UserSettingsRequest request,
            Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        UserSettingsResponse response = userSettingsService.updateSettings(userId, request);
        return ResponseEntity.ok(response);
    }

    private Long getUserIdFromAuthentication(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return userPrincipal.getId();
    }
}