package com.anime.service;

import com.anime.entity.Anime;
import com.anime.entity.Genre;
import com.anime.repository.AnimeRepository;
import com.anime.repository.FavoriteRepository;
import com.anime.repository.WatchHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final AnimeRepository animeRepository;
    private final FavoriteRepository favoriteRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String JIKAN_RECOMMENDATIONS_URL = "https://api.jikan.moe/v4/anime/%d/recommendations";
    private static final String REDIS_RECOMMENDATION_KEY = "anime:recommendation:user:%d";
    private static final long CACHE_EXPIRE_SECONDS = 3600; // 1小时

    public RecommendationService(AnimeRepository animeRepository, 
                                FavoriteRepository favoriteRepository,
                                WatchHistoryRepository watchHistoryRepository,
                                RedisTemplate<String, String> redisTemplate) {
        this.animeRepository = animeRepository;
        this.favoriteRepository = favoriteRepository;
        this.watchHistoryRepository = watchHistoryRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.redisTemplate = redisTemplate;
    }

    public List<Anime> getPopularAnime(int limit) {
        return animeRepository.findAll().stream()
                .filter(a -> a.getScore() != null)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Anime> getSeasonalAnime(String season, Integer year, int limit) {
        return animeRepository.findAll().stream()
                .filter(a -> (season == null || season.equals(a.getSeason())) &&
                            (year == null || year.equals(a.getYear())))
                .filter(a -> a.getScore() != null)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Anime> getAnimeByGenre(String genre, int limit) {
        return animeRepository.findAll().stream()
                .filter(a -> a.getGenres() != null &&
                        a.getGenres().stream()
                                .anyMatch(g -> g.getName().equals(genre)))
                .filter(a -> a.getScore() != null)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Anime> getRecommendations(Long userId, int limit) {
        if (userId == null) {
            return getPopularAnime(limit);
        }

        // 先尝试从缓存获取
        String cacheKey = String.format(REDIS_RECOMMENDATION_KEY, userId);
        List<Long> cachedIds = getCachedRecommendationIds(cacheKey);
    
        if (cachedIds != null && !cachedIds.isEmpty()) {
            // 从数据库获取动漫详情
            return animeRepository.findAllById(cachedIds).stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        // 获取用户收藏的动漫ID
        Set<Long> favoriteAnimeIds = favoriteRepository.findByUserId(userId).stream()
                .map(f -> f.getAnimeId())
                .collect(Collectors.toSet());

        // 获取用户观看历史的动漫ID
        Set<Long> watchedAnimeIds = watchHistoryRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(h -> h.getAnimeId())
                .collect(Collectors.toSet());

        // 合并收藏和观看历史的动漫ID
        Set<Long> userAnimeIds = new HashSet<>();
        userAnimeIds.addAll(favoriteAnimeIds);
        userAnimeIds.addAll(watchedAnimeIds);

        // 如果用户没有收藏或观看历史，返回热门动漫
        if (userAnimeIds.isEmpty()) {
            List<Anime> popular = getPopularAnime(limit);
            cacheRecommendationIds(cacheKey, popular.stream().map(Anime::getId).collect(Collectors.toList()));
            return popular;
        }

        // 获取用户动漫的题材
        Set<String> userGenres = getUserGenres(userAnimeIds);

        // 获取Jikan推荐的动漫（获取15个）
        Set<Long> jikanRecommendations = getJikanRecommendations(userAnimeIds, 15);

        // 获取数据库中存在的Jikan推荐
        Set<Long> jikanAnimeIdsInDb = animeRepository.findAllById(jikanRecommendations).stream()
                .filter(a -> !userAnimeIds.contains(a.getId()))
                .map(Anime::getId)
                .collect(Collectors.toSet());

        // 参考getSimilarAnime函数，按题材分组获取推荐（获取10个）
        List<Anime> genreBasedRecommendations = getGenreBasedRecommendationsV2(userGenres, userAnimeIds, 10);
        Set<Long> genreAnimeIds = genreBasedRecommendations.stream()
                .map(Anime::getId)
                .collect(Collectors.toSet());

        // 合并Jikan推荐和题材推荐的ID
        Set<Long> mergedAnimeIds = new HashSet<>();
        mergedAnimeIds.addAll(jikanAnimeIdsInDb);
        mergedAnimeIds.addAll(genreAnimeIds);

        // 如果合并后数量不足，用热门动漫补充
        if (mergedAnimeIds.size() < limit) {
            for (Anime anime : getPopularAnime(limit * 2)) {
                if (!mergedAnimeIds.contains(anime.getId()) && !userAnimeIds.contains(anime.getId())) {
                    mergedAnimeIds.add(anime.getId());
                    if (mergedAnimeIds.size() >= limit) {
                        break;
                    }
                }
            }
        }

        // 从合并的set中随机选取10个
        List<Long> shuffledIds = new ArrayList<>(mergedAnimeIds);
        Collections.shuffle(shuffledIds);
        shuffledIds = shuffledIds.stream().limit(limit).collect(Collectors.toList());

        // 获取最终推荐的动漫详情
        List<Anime> finalRecommendations = animeRepository.findAllById(shuffledIds);
        List<Long> finalRecommendationIds = shuffledIds;

        // 缓存推荐ID列表
        cacheRecommendationIds(cacheKey, finalRecommendationIds);

        return finalRecommendations;
    }

    private void cacheRecommendationIds(String cacheKey, List<Long> animeIds) {
        try {
            String json = objectMapper.writeValueAsString(animeIds);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            // 缓存失败不影响业务
        }
    }

    private List<Long> getCachedRecommendationIds(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
            }
        } catch (JsonProcessingException e) {
            // 解析失败，返回null重新计算
        }
        return null;
    }

    public void invalidateUserRecommendations(Long userId) {
        String cacheKey = String.format(REDIS_RECOMMENDATION_KEY, userId);
        redisTemplate.delete(cacheKey);
    }

    private Set<String> getUserGenres(Set<Long> animeIds) {
        Set<String> genres = new HashSet<>();
        List<Anime> userAnimeList = animeRepository.findAllById(animeIds);
        for (Anime anime : userAnimeList) {
            if (anime.getGenres() != null) {
                for (Genre genre : anime.getGenres()) {
                    genres.add(genre.getName());
                }
            }
        }
        return genres;
    }

    private Set<Long> getJikanRecommendations(Set<Long> animeIds, int maxResults) {
        Set<Long> recommendations = new HashSet<>();
        
        for (Long animeId : animeIds) {
            try {
                String url = String.format(JIKAN_RECOMMENDATIONS_URL, animeId);
                String response = restTemplate.getForObject(url, String.class);
                
                if (response != null) {
                    JsonNode root = objectMapper.readTree(response);
                    JsonNode dataArray = root.get("data");
                    
                    if (dataArray != null && !dataArray.isEmpty()) {
                        for (JsonNode entryNode : dataArray) {
                            JsonNode entry = entryNode.get("entry");
                            if (entry != null) {
                                JsonNode malIdNode = entry.get("mal_id");
                                if (malIdNode != null && !malIdNode.isNull()) {
                                    recommendations.add(malIdNode.asLong());
                                    if (recommendations.size() >= maxResults) {
                                        return recommendations;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Jikan API调用失败，跳过
                continue;
            }
        }
        
        return recommendations;
    }

    private List<Anime> getGenreBasedRecommendationsV2(Set<String> genres, Set<Long> excludeIds, int limit) {
        if (genres.isEmpty()) {
            return getPopularAnime(limit);
        }

        // 参考getSimilarAnime函数，按题材分组获取推荐
        int genreCount = genres.size();
        int maxPerGenre = (int) Math.ceil(8.0 / genreCount);

        Set<Long> selectedAnimeIds = new HashSet<>(excludeIds);
        List<Anime> results = new ArrayList<>();

        for (String targetGenre : genres) {
            List<Anime> genreResults = animeRepository.findAll().stream()
                    .filter(a -> !selectedAnimeIds.contains(a.getId()))
                    .filter(a -> a.getGenres() != null)
                    .filter(a -> a.getScore() != null)
                    .filter(a -> a.getGenres().stream()
                            .anyMatch(g -> g.getName().equals(targetGenre)))
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(maxPerGenre)
                    .collect(Collectors.toList());

            for (Anime result : genreResults) {
                if (!selectedAnimeIds.contains(result.getId())) {
                    selectedAnimeIds.add(result.getId());
                    results.add(result);
                    if (results.size() >= limit) {
                        return results;
                    }
                }
            }
        }

        // 如果按题材获取的数量不够，用热门动漫补充
        if (results.size() < limit) {
            for (Anime popularAnime : getPopularAnime(limit * 2)) {
                if (!selectedAnimeIds.contains(popularAnime.getId())) {
                    results.add(popularAnime);
                    if (results.size() >= limit) {
                        break;
                    }
                }
            }
        }

        return results;
    }

    public List<Anime> getSimilarAnime(Long animeId, int limit) {
        Optional<Anime> targetAnime = animeRepository.findById(animeId);
        if (targetAnime.isEmpty()) {
            return getPopularAnime(limit);
        }

        Anime anime = targetAnime.get();
        
        // 处理题材为空的情况
        if (anime.getGenres() == null || anime.getGenres().isEmpty()) {
            return getPopularAnime(limit);
        }

        Set<String> targetGenreNames = anime.getGenres().stream()
                .map(Genre::getName)
                .filter(genreName -> genreName != null && !genreName.trim().isEmpty())
                .collect(Collectors.toSet());

        // 确保题材列表不为空
        if (targetGenreNames.isEmpty()) {
            return getPopularAnime(limit);
        }

        // 计算每种题材最多获取的数量：8 / 题材数量
        int genreCount = targetGenreNames.size();
        int maxPerGenre = (int) Math.ceil(8.0 / genreCount);

        // 按题材分组获取相似动漫，确保不重复
        Set<Long> selectedAnimeIds = new HashSet<>();
        selectedAnimeIds.add(animeId); // 排除原动漫
        List<Anime> results = new ArrayList<>();

        for (String targetGenre : targetGenreNames) {
            List<Anime> genreResults = animeRepository.findAll().stream()
                    .filter(a -> !selectedAnimeIds.contains(a.getId()))
                    .filter(a -> a.getGenres() != null)
                    .filter(a -> a.getScore() != null)
                    .filter(a -> a.getGenres().stream()
                            .anyMatch(g -> g.getName().equals(targetGenre)))
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(maxPerGenre)
                    .collect(Collectors.toList());

            for (Anime result : genreResults) {
                if (!selectedAnimeIds.contains(result.getId())) {
                    selectedAnimeIds.add(result.getId());
                    results.add(result);
                    if (results.size() >= limit) {
                        return results;
                    }
                }
            }
        }

        // 如果按题材获取的数量不够，用热门动漫补充
        if (results.size() < limit) {
            for (Anime popularAnime : getPopularAnime(limit)) {
                if (!selectedAnimeIds.contains(popularAnime.getId())) {
                    results.add(popularAnime);
                    if (results.size() >= limit) {
                        break;
                    }
                }
            }
        }

        return results;
    }
}