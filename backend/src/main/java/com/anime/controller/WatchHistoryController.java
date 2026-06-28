package com.anime.controller;

import com.anime.dto.UserPrincipal;
import com.anime.dto.WatchHistoryDto;
import com.anime.dto.response.ApiResponse;
import com.anime.entity.WatchHistory;
import com.anime.service.WatchHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watch-history")
public class WatchHistoryController {

    @Autowired
    private WatchHistoryService watchHistoryService;

    @PostMapping("/progress")
    public ResponseEntity<ApiResponse<WatchHistoryDto>> saveProgress(
            Authentication authentication,
            @RequestParam Long animeId,
            @RequestParam Long episodeId,
            @RequestParam Integer episodeNumber,
            @RequestParam(required = false) Integer progress,
            @RequestParam(required = false) Boolean completed) {
        Long userId = getCurrentUserId(authentication);
        WatchHistory history = watchHistoryService.saveOrUpdateProgress(userId, animeId, episodeId, episodeNumber, progress, completed);
        return ResponseEntity.ok(ApiResponse.success(toDto(history)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WatchHistoryDto>>> getCurrentUserHistory(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        List<WatchHistory> histories = watchHistoryService.getByUserId(userId);
        List<WatchHistoryDto> dtos = histories.stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @GetMapping("/anime/{animeId}")
    public ResponseEntity<ApiResponse<WatchHistoryDto>> getByCurrentUserAndAnimeId(
            Authentication authentication,
            @PathVariable Long animeId) {
        Long userId = getCurrentUserId(authentication);
        return watchHistoryService.getByUserIdAndAnimeId(userId, animeId)
                .map(history -> ResponseEntity.ok(ApiResponse.success(toDto(history))))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteById(
            Authentication authentication,
            @PathVariable Long id) {
        Long userId = getCurrentUserId(authentication);
        watchHistoryService.deleteByIdAndUser(id, userId);
        return ResponseEntity.ok(ApiResponse.success("", null));
    }

    @DeleteMapping("/anime/{animeId}")
    public ResponseEntity<ApiResponse<Void>> deleteByCurrentUserAndAnimeId(
            Authentication authentication,
            @PathVariable Long animeId) {
        Long userId = getCurrentUserId(authentication);
        watchHistoryService.deleteByUserIdAndAnimeId(userId, animeId);
        return ResponseEntity.ok(ApiResponse.success("", null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCurrentUserHistory(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        watchHistoryService.deleteByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("", null));
    }

    private Long getCurrentUserId(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return userPrincipal.getId();
    }

    private WatchHistoryDto toDto(WatchHistory entity) {
        WatchHistoryDto dto = new WatchHistoryDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setAnimeId(entity.getAnimeId());
        dto.setEpisodeId(entity.getEpisodeId());
        dto.setEpisodeNumber(entity.getEpisodeNumber());
        dto.setProgress(entity.getProgress());
        dto.setCompleted(entity.getCompleted());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
