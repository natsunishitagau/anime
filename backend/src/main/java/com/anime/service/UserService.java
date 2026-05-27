package com.anime.service;

import com.anime.dto.DtoMapper;
import com.anime.entity.Anime;
import com.anime.entity.Favorite;
import com.anime.entity.FavoriteFolder;
import com.anime.entity.WatchHistory;
import com.anime.repository.AnimeRepository;
import com.anime.repository.AnimeVideoRepository;
import com.anime.repository.FavoriteFolderRepository;
import com.anime.repository.FavoriteRepository;
import com.anime.repository.WatchHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final FavoriteRepository favoriteRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final AnimeRepository animeRepository;
    private final AnimeVideoRepository animeVideoRepository;
    private final RecommendationService recommendationService;
    private final DtoMapper dtoMapper;
    private final FavoriteFolderRepository favoriteFolderRepository;

    public UserService(FavoriteRepository favoriteRepository,
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

    public List<Map<String, Object>> getFavoriteFolders(Long userId) {
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

    public List<Anime> getFavoritesByFolder(Long userId, Long folderId) {
        List<Favorite> favorites = favoriteRepository.findByUserIdAndFolderIdOrderByCreatedAtDesc(userId, folderId);
        return favorites.stream()
                .map(f -> animeRepository.findById(f.getAnimeId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void addFavoriteToFolder(Long userId, Long animeId, Long folderId) {
        if (favoriteRepository.existsByUserIdAndAnimeIdAndFolderId(userId, animeId, folderId)) {
            return;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setAnimeId(animeId);
        favorite.setFolderId(folderId);
        favoriteRepository.save(favorite);
    }

    public void removeFavoriteFromFolder(Long userId, Long animeId, Long folderId) {
        favoriteRepository.findByUserIdAndAnimeIdAndFolderId(userId, animeId, folderId)
                .ifPresent(favoriteRepository::delete);
    }

    public void deleteFolderAndFavorites(Long userId, Long folderId) {
        List<Favorite> favorites = favoriteRepository.findByUserIdAndFolderIdOrderByCreatedAtDesc(userId, folderId);
        favoriteRepository.deleteAll(favorites);
    }

    public List<Anime> getFavorites(Long userId) {
        List<Favorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return favorites.stream()
                .map(f -> animeRepository.findById(f.getAnimeId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void removeFavorite(Long userId, Long animeId) {
        favoriteRepository.findByUserIdAndAnimeId(userId, animeId)
                .ifPresent(favoriteRepository::delete);
    }

    public List<Map<String, Object>> getWatchHistory(Long userId) {
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
                        map.put("episodeId", h.getEpisodeId());

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

    public void updateWatchProgress(Long userId, Long animeId, int progress, boolean completed) {
        Optional<WatchHistory> existing = watchHistoryRepository.findByUserIdAndAnimeId(userId, animeId);
        WatchHistory history = existing.orElse(new WatchHistory());
        history.setUserId(userId);
        history.setAnimeId(animeId);
        history.setProgress(progress);
        history.setCompleted(completed);
        watchHistoryRepository.save(history);
    }

    public List<Anime> getRecommendations(Long userId, int limit) {
        return recommendationService.getRecommendations(userId, limit);
    }
}
