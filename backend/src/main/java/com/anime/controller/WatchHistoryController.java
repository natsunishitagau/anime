package com.anime.controller;

import com.anime.dto.ApiResponse;
import com.anime.dto.WatchHistoryDto;
import com.anime.entity.WatchHistory;
import com.anime.service.WatchHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watch-history")
@CrossOrigin
public class WatchHistoryController {

    @Autowired
    private WatchHistoryService watchHistoryService;

    @PostMapping("/progress")
    public ResponseEntity<ApiResponse<WatchHistoryDto>> saveProgress(
            @RequestParam Long userId,
            @RequestParam Long animeId,
            @RequestParam Long episodeId,
            @RequestParam Integer episodeNumber,
            @RequestParam(required = false) Integer progress,
            @RequestParam(required = false) Boolean completed) {
        WatchHistory history = watchHistoryService.saveOrUpdateProgress(userId, animeId, episodeId, episodeNumber, progress, completed);
        return ResponseEntity.ok(ApiResponse.success(toDto(history)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<WatchHistoryDto>>> getByUserId(@PathVariable Long userId) {
        List<WatchHistory> histories = watchHistoryService.getByUserId(userId);
        List<WatchHistoryDto> dtos = histories.stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @GetMapping("/user/{userId}/anime/{animeId}")
    public ResponseEntity<ApiResponse<WatchHistoryDto>> getByUserIdAndAnimeId(
            @PathVariable Long userId,
            @PathVariable Long animeId) {
        return watchHistoryService.getByUserIdAndAnimeId(userId, animeId)
                .map(history -> ResponseEntity.ok(ApiResponse.success(toDto(history))))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteById(@PathVariable Long id) {
        watchHistoryService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
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
