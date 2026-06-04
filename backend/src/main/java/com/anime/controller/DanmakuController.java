package com.anime.controller;

import com.anime.dto.DanmakuDto;
import com.anime.dto.UserPrincipal;
import com.anime.dto.request.DanmakuRequest;
import com.anime.dto.response.ApiResponse;
import com.anime.service.DanmakuRateService;
import com.anime.service.DanmakuService;
import com.anime.util.SensitiveWordFilter;
import com.anime.websocket.DanmakuWebSocketHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/danmaku")
public class DanmakuController {

    private final DanmakuService danmakuService;
    private final DanmakuWebSocketHandler danmakuWebSocketHandler;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final DanmakuRateService danmakuRateLimiter;

    public DanmakuController(DanmakuService danmakuService, DanmakuWebSocketHandler danmakuWebSocketHandler, 
                            SensitiveWordFilter sensitiveWordFilter, DanmakuRateService danmakuRateLimiter) {
        this.danmakuService = danmakuService;
        this.danmakuWebSocketHandler = danmakuWebSocketHandler;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.danmakuRateLimiter = danmakuRateLimiter;
    }

    @GetMapping("/video/{videoId}")
    public ResponseEntity<ApiResponse<List<DanmakuDto>>> getDanmakuByVideoId(
            @PathVariable Long videoId,
            Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        List<DanmakuDto> danmakuList = danmakuService.getDanmakuByVideoId(videoId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(danmakuList));
    }

    @GetMapping("/video/{videoId}/minute/{minute}")
    public ResponseEntity<ApiResponse<List<DanmakuDto>>> getDanmakuByVideoIdAndMinute(
            @PathVariable Long videoId,
            @PathVariable int minute,
            Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        List<DanmakuDto> danmakuList = danmakuService.getDanmakuByVideoIdAndMinute(videoId, minute, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(danmakuList));
    }

    @GetMapping("/video/{videoId}/range")
    public ResponseEntity<ApiResponse<List<DanmakuDto>>> getDanmakuByVideoIdAndTimeRange(
            @PathVariable Long videoId,
            @RequestParam Double startTime,
            @RequestParam Double endTime,
            Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        List<DanmakuDto> danmakuList = danmakuService.getDanmakuByVideoIdAndTimeRange(videoId, startTime, endTime, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(danmakuList));
    }

    @GetMapping("/video/{videoId}/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getDanmakuCount(@PathVariable Long videoId) {
        long count = danmakuService.countDanmakuByVideoId(videoId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DanmakuDto>> sendDanmaku(
            @RequestBody DanmakuRequest request,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Content cannot be empty"));
        }
        if (request.getContent().length() > 50) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Content cannot exceed 50 characters"));
        }
        if (request.getTime() == null || request.getTime() < 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid time"));
        }

        if (!sensitiveWordFilter.validate(request.getContent())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Content contains sensitive words"));
        }

        if (!danmakuRateLimiter.tryAcquire(userPrincipal.getId(), request.getVideoId())) {
            return ResponseEntity.status(429).body(ApiResponse.error("Too many requests"));
        }

        DanmakuDto danmaku = danmakuService.sendDanmaku(userPrincipal.getId(), request);
        danmakuWebSocketHandler.broadcastDanmaku(String.valueOf(request.getVideoId()), danmaku);
        return ResponseEntity.ok(ApiResponse.success("Danmaku sent successfully", danmaku));
    }

    @DeleteMapping("/{danmakuId}")
    public ResponseEntity<ApiResponse<Void>> deleteDanmaku(
            @PathVariable Long danmakuId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        Long videoId = danmakuService.deleteDanmaku(userPrincipal.getId(), danmakuId);
        if (videoId != null) {
            danmakuWebSocketHandler.broadcastDanmaku(String.valueOf(videoId),
                Map.of("type", "delete", "danmakuId", danmakuId, "videoId", videoId));
            return ResponseEntity.ok(ApiResponse.success("Danmaku deleted", null));
        } else {
            return ResponseEntity.status(403).body(ApiResponse.error("Cannot delete this danmaku"));
        }
    }

    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            return null;
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return userPrincipal.getId();
    }
}