package com.anime.service;

import com.anime.dto.request.UserSettingsRequest;
import com.anime.dto.response.UserSettingsResponse;
import com.anime.entity.User;
import com.anime.entity.UserSettings;
import com.anime.repository.UserRepository;
import com.anime.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
        return convertToResponse(settings);
    }

    @Transactional
    public UserSettingsResponse updateSettings(Long userId, UserSettingsRequest request) {
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        if (request.getDanmakuEnabled() != null) {
            settings.setDanmakuEnabled(request.getDanmakuEnabled());
        }
        if (request.getDanmakuColor() != null && !request.getDanmakuColor().isEmpty()) {
            settings.setDanmakuColor(request.getDanmakuColor());
        }
        if (request.getDefaultVolume() != null) {
            settings.setDefaultVolume(request.getDefaultVolume());
        }

        UserSettings savedSettings = userSettingsRepository.save(settings);
        return convertToResponse(savedSettings);
    }

    @Transactional
    public void createSettingsForUser(Long userId) {
        if (!userSettingsRepository.existsByUserId(userId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            UserSettings settings = UserSettings.builder()
                    .user(user)
                    .danmakuEnabled(true)
                    .danmakuColor("#FFFFFF")
                    .defaultVolume(0.8)
                    .build();

            userSettingsRepository.save(settings);
        }
    }

    private UserSettings createDefaultSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserSettings settings = UserSettings.builder()
                .user(user)
                .danmakuEnabled(true)
                .danmakuColor("#FFFFFF")
                .defaultVolume(0.8)
                .build();

        return userSettingsRepository.save(settings);
    }

    private UserSettingsResponse convertToResponse(UserSettings settings) {
        return UserSettingsResponse.builder()
                .danmakuEnabled(settings.getDanmakuEnabled())
                .danmakuColor(settings.getDanmakuColor())
                .defaultVolume(settings.getDefaultVolume())
                .build();
    }
}