package com.anime.controller;

import com.anime.service.AnimeSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AnimeSyncController {

    private final AnimeSyncService animeSyncService;

    public AnimeSyncController(AnimeSyncService animeSyncService) {
        this.animeSyncService = animeSyncService;
    }

    @PostMapping("/sync/anime")
    public ResponseEntity<String> syncAnime(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int limit) {
        String result = animeSyncService.syncTopAnime(page, limit);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync/anime/all")
    public ResponseEntity<String> syncAllAnime(
            @RequestParam(defaultValue = "10") int maxPages) {
        String result = animeSyncService.syncAllAnime(maxPages);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/clean/anime")
    public ResponseEntity<String> cleanAnimeData() {
        String result = animeSyncService.cleanAnimeData();
        return ResponseEntity.ok(result);
    }
}