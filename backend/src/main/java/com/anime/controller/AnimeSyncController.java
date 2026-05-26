package com.anime.controller;

import com.anime.service.AnimeSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @PostMapping("/sync/anime/{animeId}/characters")
    public ResponseEntity<String> syncAnimeCharacters(@PathVariable Long animeId) {
        String result = animeSyncService.syncAnimeCharacters(animeId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync/anime/{animeId}/genres")
    public ResponseEntity<String> syncAnimeGenres(@PathVariable Long animeId) {
        String result = animeSyncService.syncAnimeGenres(animeId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync/genres/all")
    public ResponseEntity<String> syncAllAnimeGenres() {
        String result = animeSyncService.syncAllAnimeGenres();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync/anime/id/{animeId}")
    public ResponseEntity<String> syncAnimeById(@PathVariable Long animeId) {
        String result = animeSyncService.syncAnimeById(animeId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync/anime/year/missing")
    public ResponseEntity<String> syncAnimeYearFromApi() {
        String result = animeSyncService.syncAnimeYearFromApi();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync/anime/{animeId}/year")
    public ResponseEntity<String> syncAnimeYearById(@PathVariable Long animeId) {
        String result = animeSyncService.syncAnimeYearById(animeId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/sync/anime/year/progress")
    public ResponseEntity<Map<String, Object>> getSyncProgress() {
        Map<String, Object> progress = animeSyncService.getSyncProgress();
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/sync/anime/year/stop")
    public ResponseEntity<String> stopSync() {
        animeSyncService.stopSync();
        return ResponseEntity.ok("已发送停止同步命令");
    }
}
