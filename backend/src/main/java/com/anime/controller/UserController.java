package com.anime.controller;

import com.anime.dto.ApiResponse;
import com.anime.dto.AnimeDto;
import com.anime.dto.DtoMapper;
import com.anime.dto.UserPrincipal;
import com.anime.entity.Anime;
import com.anime.entity.Favorite;
import com.anime.entity.WatchHistory;
import com.anime.repository.AnimeRepository;
import com.anime.repository.FavoriteRepository;
import com.anime.repository.UserRepository;
import com.anime.repository.WatchHistoryRepository;
import com.anime.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserControllerHelper helper;
    private final UserRepository userRepository;

    public UserController(FavoriteRepository favoriteRepository, 
                          WatchHistoryRepository watchHistoryRepository,
                          AnimeRepository animeRepository,
                          RecommendationService recommendationService,
                          UserRepository userRepository) {
        this.helper = new UserControllerHelper(favoriteRepository, watchHistoryRepository, animeRepository, recommendationService);
        this.userRepository = userRepository;
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getFavorites(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toAnimeDtoList(helper.getFavorites(userPrincipal.getId()))));
    }

    @DeleteMapping("/favorites/{animeId}")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @PathVariable Long animeId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        helper.removeFavorite(userPrincipal.getId(), animeId);
        return ResponseEntity.ok(ApiResponse.success("Removed from favorites", null));
    }

    @GetMapping("/watch-history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getWatchHistory(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(helper.getWatchHistory(userPrincipal.getId())));
    }

    @PostMapping("/watch-history/{animeId}")
    public ResponseEntity<ApiResponse<Void>> updateWatchProgress(
            @PathVariable Long animeId,
            @RequestParam int progress,
            @RequestParam(defaultValue = "false") boolean completed,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        helper.updateWatchProgress(userPrincipal.getId(), animeId, progress, completed);
        return ResponseEntity.ok(ApiResponse.success("Progress updated", null));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getUserRecommendations(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toAnimeDtoList(helper.getRecommendations(userPrincipal.getId(), limit))));
    }

    @PutMapping("/signature")
    public ResponseEntity<ApiResponse<Void>> updateSignature(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        String signature = body.get("signature");
        if (signature != null && signature.length() > 100) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Signature must be within 100 characters"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        userRepository.findById(userPrincipal.getId()).ifPresent(user -> {
            user.setSignature(signature);
            userRepository.save(user);
        });

        return ResponseEntity.ok(ApiResponse.success("Signature updated", null));
    }

    static class UserControllerHelper {
        private final FavoriteRepository favoriteRepository;
        private final WatchHistoryRepository watchHistoryRepository;
        private final AnimeRepository animeRepository;
        private final RecommendationService recommendationService;

        UserControllerHelper(FavoriteRepository favoriteRepository,
                            WatchHistoryRepository watchHistoryRepository,
                            AnimeRepository animeRepository,
                            RecommendationService recommendationService) {
            this.favoriteRepository = favoriteRepository;
            this.watchHistoryRepository = watchHistoryRepository;
            this.animeRepository = animeRepository;
            this.recommendationService = recommendationService;
        }

        List<Anime> getFavorites(Long userId) {
            List<Favorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
            return favorites.stream()
                    .map(f -> animeRepository.findById(f.getAnimeId()).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        void removeFavorite(Long userId, Long animeId) {
            favoriteRepository.findByUserIdAndAnimeId(userId, animeId)
                    .ifPresent(favoriteRepository::delete);
        }

        List<Map<String, Object>> getWatchHistory(Long userId) {
            List<WatchHistory> history = watchHistoryRepository.findByUserIdOrderByUpdatedAtDesc(userId);
            return history.stream()
                    .map(h -> {
                        Map<String, Object> map = new HashMap<>();
                        animeRepository.findById(h.getAnimeId()).ifPresent(anime -> {
                            map.put("anime", DtoMapper.toAnimeDto(anime));
                            map.put("progress", h.getProgress());
                            map.put("completed", h.getCompleted());
                            map.put("updatedAt", h.getUpdatedAt());
                        });
                        return map;
                    })
                    .filter(m -> m.containsKey("anime"))
                    .collect(Collectors.toList());
        }

        void updateWatchProgress(Long userId, Long animeId, int progress, boolean completed) {
            Optional<WatchHistory> existing = watchHistoryRepository.findByUserIdAndAnimeId(userId, animeId);
            WatchHistory history = existing.orElse(new WatchHistory());
            history.setUserId(userId);
            history.setAnimeId(animeId);
            history.setProgress(progress);
            history.setCompleted(completed);
            watchHistoryRepository.save(history);
        }

        List<Anime> getRecommendations(Long userId, int limit) {
            return recommendationService.getRecommendations(userId, limit);
        }
    }
}