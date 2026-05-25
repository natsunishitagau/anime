package com.anime.controller;

import com.anime.dto.ApiResponse;
import com.anime.dto.AnimeDto;
import com.anime.dto.DtoMapper;
import com.anime.dto.FolderReorderRequest;
import com.anime.dto.UserPrincipal;
import com.anime.entity.Anime;
import com.anime.entity.Favorite;
import com.anime.entity.FavoriteFolder;
import com.anime.entity.User;
import com.anime.entity.WatchHistory;
import com.anime.repository.AnimeRepository;
import com.anime.repository.AnimeVideoRepository;
import com.anime.repository.FavoriteFolderRepository;
import com.anime.repository.FavoriteRepository;
import com.anime.repository.UserRepository;
import com.anime.repository.WatchHistoryRepository;
import com.anime.service.RecommendationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserControllerHelper helper;
    private final UserRepository userRepository;
    private final DtoMapper dtoMapper;
    private final FavoriteFolderRepository favoriteFolderRepository;

    public UserController(FavoriteRepository favoriteRepository, 
                          WatchHistoryRepository watchHistoryRepository,
                          AnimeRepository animeRepository,
                          AnimeVideoRepository animeVideoRepository,
                          RecommendationService recommendationService,
                          UserRepository userRepository,
                          DtoMapper dtoMapper,
                          FavoriteFolderRepository favoriteFolderRepository) {
        this.helper = new UserControllerHelper(favoriteRepository, watchHistoryRepository, animeRepository, animeVideoRepository, recommendationService, dtoMapper, favoriteFolderRepository);
        this.userRepository = userRepository;
        this.dtoMapper = dtoMapper;
        this.favoriteFolderRepository = favoriteFolderRepository;
    }

    @GetMapping("/favorites/folders")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFavoriteFolders(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        List<Map<String, Object>> folders = helper.getFavoriteFolders(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(folders));
    }

    @PostMapping("/favorites/folders")
    public ResponseEntity<ApiResponse<FavoriteFolder>> createFavoriteFolder(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder name is required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        if (favoriteFolderRepository.existsByUserIdAndName(userPrincipal.getId(), name.trim())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder name already exists"));
        }
        List<FavoriteFolder> existingFolders = favoriteFolderRepository.findByUserIdOrderBySortOrderAsc(userPrincipal.getId());
        for (FavoriteFolder f : existingFolders) {
            f.setSortOrder(f.getSortOrder() + 1);
            favoriteFolderRepository.save(f);
        }
        FavoriteFolder folder = new FavoriteFolder();
        folder.setUserId(userPrincipal.getId());
        folder.setName(name.trim());
        folder.setSortOrder(0);
        FavoriteFolder savedFolder = favoriteFolderRepository.save(folder);
        return ResponseEntity.ok(ApiResponse.success("Folder created", savedFolder));
    }

    @PutMapping("/favorites/folders/{folderId}")
    public ResponseEntity<ApiResponse<FavoriteFolder>> updateFavoriteFolder(
            @PathVariable Long folderId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder name is required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
        if (folderOpt.isEmpty() || !folderOpt.get().getUserId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(404).body(ApiResponse.error("Folder not found"));
        }
        String trimmedName = name.trim();
        if (favoriteFolderRepository.existsByUserIdAndName(userPrincipal.getId(), trimmedName)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder name already exists"));
        }
        FavoriteFolder folder = folderOpt.get();
        folder.setName(trimmedName);
        FavoriteFolder updatedFolder = favoriteFolderRepository.save(folder);
        return ResponseEntity.ok(ApiResponse.success("Folder renamed", updatedFolder));
    }

    @DeleteMapping("/favorites/folders/{folderId}")
    public ResponseEntity<ApiResponse<Void>> deleteFavoriteFolder(
            @PathVariable Long folderId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
        if (folderOpt.isEmpty() || !folderOpt.get().getUserId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(404).body(ApiResponse.error("Folder not found"));
        }
        helper.deleteFolderAndFavorites(userPrincipal.getId(), folderId);
        favoriteFolderRepository.deleteById(folderId);
        return ResponseEntity.ok(ApiResponse.success("Folder deleted", null));
    }

    @PutMapping("/favorites/folders/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderFavoriteFolders(
            @RequestBody FolderReorderRequest request,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        List<Long> folderOrder = request.getFolderOrder();
        if (folderOrder == null || folderOrder.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Folder order is required"));
        }
        for (int i = 0; i < folderOrder.size(); i++) {
            Long folderId = folderOrder.get(i);
            Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
            if (folderOpt.isPresent() && folderOpt.get().getUserId().equals(userPrincipal.getId())) {
                folderOpt.get().setSortOrder(i);
                favoriteFolderRepository.save(folderOpt.get());
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Folders reordered", null));
    }

    @GetMapping("/favorites/folders/{folderId}")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getFavoritesByFolder(
            @PathVariable Long folderId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
        if (folderOpt.isEmpty() || !folderOpt.get().getUserId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(404).body(ApiResponse.error("Folder not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(dtoMapper.toAnimeDtoList(helper.getFavoritesByFolder(userPrincipal.getId(), folderId))));
    }

    @PostMapping("/favorites/folders/{folderId}/anime/{animeId}")
    public ResponseEntity<ApiResponse<Void>> addFavoriteToFolder(
            @PathVariable Long folderId,
            @PathVariable Long animeId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<FavoriteFolder> folderOpt = favoriteFolderRepository.findById(folderId);
        if (folderOpt.isEmpty() || !folderOpt.get().getUserId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(404).body(ApiResponse.error("Folder not found"));
        }
        helper.addFavoriteToFolder(userPrincipal.getId(), animeId, folderId);
        return ResponseEntity.ok(ApiResponse.success("Added to folder", null));
    }

    @DeleteMapping("/favorites/folders/{folderId}/anime/{animeId}")
    public ResponseEntity<ApiResponse<Void>> removeFavoriteFromFolder(
            @PathVariable Long folderId,
            @PathVariable Long animeId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        helper.removeFavoriteFromFolder(userPrincipal.getId(), animeId, folderId);
        return ResponseEntity.ok(ApiResponse.success("Removed from folder", null));
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getFavorites(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(dtoMapper.toAnimeDtoList(helper.getFavorites(userPrincipal.getId()))));
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
        return ResponseEntity.ok(ApiResponse.success(dtoMapper.toAnimeDtoList(helper.getRecommendations(userPrincipal.getId(), limit))));
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

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> updateAvatar(
            @RequestParam("avatar") MultipartFile file,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("User not found"));
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        String newFilename = UUID.randomUUID().toString() + extension;
        
        try {
            Path uploadDir = Paths.get("uploads/avatars");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            
            Path targetLocation = uploadDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            
            String avatarUrl = "/uploads/avatars/" + newFilename;
            
            String oldAvatarUrl = user.getAvatarUrl();
            if (oldAvatarUrl != null && oldAvatarUrl.startsWith("/uploads/avatars/")) {
                String oldFilename = oldAvatarUrl.substring("/uploads/avatars/".length());
                Path oldFilePath = uploadDir.resolve(oldFilename);
                if (Files.exists(oldFilePath)) {
                    Files.delete(oldFilePath);
                }
            }
            
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);
            
            return ResponseEntity.ok(ApiResponse.success("Avatar updated", avatarUrl));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to upload avatar"));
        }
    }

    static class UserControllerHelper {
        private final FavoriteRepository favoriteRepository;
        private final WatchHistoryRepository watchHistoryRepository;
        private final AnimeRepository animeRepository;
        private final AnimeVideoRepository animeVideoRepository;
        private final RecommendationService recommendationService;
        private final DtoMapper dtoMapper;
        private final FavoriteFolderRepository favoriteFolderRepository;

        UserControllerHelper(FavoriteRepository favoriteRepository,
                            WatchHistoryRepository watchHistoryRepository,
                            AnimeRepository animeRepository,
                            AnimeVideoRepository animeVideoRepository,
                            RecommendationService recommendationService,
                            DtoMapper dtoMapper,
                            FavoriteFolderRepository favoriteFolderRepository) {
            this.favoriteRepository = favoriteRepository;
            this.watchHistoryRepository = watchHistoryRepository;
            this.animeRepository = animeRepository;
            this.animeVideoRepository = animeVideoRepository;
            this.recommendationService = recommendationService;
            this.dtoMapper = dtoMapper;
            this.favoriteFolderRepository = favoriteFolderRepository;
        }

        List<Map<String, Object>> getFavoriteFolders(Long userId) {
            List<FavoriteFolder> folders = favoriteFolderRepository.findByUserIdOrderBySortOrderAsc(userId);
            return folders.stream()
                    .map(folder -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", folder.getId());
                        map.put("name", folder.getName());
                        map.put("createdAt", folder.getCreatedAt());
                        List<Anime> animes = getFavoritesByFolder(userId, folder.getId());
                        map.put("count", animes.size());
                        if (!animes.isEmpty()) {
                            map.put("latestAnimeImage", animes.get(0).getImageUrl());
                        }
                        return map;
                    })
                    .collect(Collectors.toList());
        }

        List<Anime> getFavoritesByFolder(Long userId, Long folderId) {
            List<Favorite> favorites = favoriteRepository.findByUserIdAndFolderIdOrderByCreatedAtDesc(userId, folderId);
            return favorites.stream()
                    .map(f -> animeRepository.findById(f.getAnimeId()).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        void addFavoriteToFolder(Long userId, Long animeId, Long folderId) {
            if (favoriteRepository.existsByUserIdAndAnimeIdAndFolderId(userId, animeId, folderId)) {
                return;
            }
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setAnimeId(animeId);
            favorite.setFolderId(folderId);
            favoriteRepository.save(favorite);
        }

        void removeFavoriteFromFolder(Long userId, Long animeId, Long folderId) {
            favoriteRepository.findByUserIdAndAnimeIdAndFolderId(userId, animeId, folderId)
                    .ifPresent(favoriteRepository::delete);
        }

        void deleteFolderAndFavorites(Long userId, Long folderId) {
            List<Favorite> favorites = favoriteRepository.findByUserIdAndFolderIdOrderByCreatedAtDesc(userId, folderId);
            favoriteRepository.deleteAll(favorites);
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
                            map.put("anime", dtoMapper.toAnimeDto(anime));
                            map.put("progress", h.getProgress());
                            map.put("completed", h.getCompleted());
                            map.put("updatedAt", h.getUpdatedAt());
                            map.put("episodeNumber", h.getEpisodeNumber());

                            int duration = 0;
                            if (h.getEpisodeId() != null) {
                                duration = animeVideoRepository.findById(h.getEpisodeId())
                                        .map(v -> v.getDuration() != null ? v.getDuration() : 0)
                                        .orElse(0);
                            }
                            int progressPercent = 0;
                            if (h.getProgress() != null && duration > 0) {
                                progressPercent = Math.min(h.getProgress() * 100 / duration, 100);
                            }
                            map.put("duration", duration);
                            map.put("progressPercent", progressPercent);
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