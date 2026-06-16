package com.anime.service;

import com.anime.entity.Anime;
import com.anime.entity.Genre;
import com.anime.repository.AnimeRepository;
import com.anime.repository.AnimeSpecifications;
import com.anime.repository.FavoriteRepository;
import com.anime.repository.WatchHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final String JIKAN_RECOMMENDATIONS_URL = "https://api.jikan.moe/v4/anime/%d/recommendations";
    private static final String REDIS_RECOMMENDATION_KEY = "anime:recommendation:user:%d";
    private static final long CACHE_EXPIRE_SECONDS = 3600;

    private final AnimeRepository animeRepository;
    private final FavoriteRepository favoriteRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public RecommendationService(AnimeRepository animeRepository,
                                 FavoriteRepository favoriteRepository,
                                 WatchHistoryRepository watchHistoryRepository,
                                 RedisTemplate<String, String> redisTemplate,
                                 RestTemplateBuilder restTemplateBuilder,
                                 ObjectMapper objectMapper) {
        this.animeRepository = animeRepository;
        this.favoriteRepository = favoriteRepository;
        this.watchHistoryRepository = watchHistoryRepository;
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public List<Anime> getPopularAnime(int limit) {
        return animeRepository.findTopRated(PageRequest.of(0, limit));
    }

    public List<Anime> getSeasonalAnime(String season, Integer year, int limit) {
        if (season != null && year != null) {
            return animeRepository.findBySeasonAndYear(season, year).stream()
                    .filter(a -> a.getScore() != null)
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        return animeRepository.findAll().stream()
                .filter(a -> (season == null || season.equals(a.getSeason())) &&
                            (year == null || year.equals(a.getYear())))
                .filter(a -> a.getScore() != null)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Anime> getAnimeByGenre(String genre, int limit) {
        var spec = AnimeSpecifications.hasGenre(genre)
                .and(AnimeSpecifications.hasScoreNotNull());
        return animeRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "score")).stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Anime> getRecommendations(Long userId, int limit) {
        if (userId == null) {
            return getPopularAnime(limit);
        }

        String cacheKey = String.format(REDIS_RECOMMENDATION_KEY, userId);
        List<Long> cachedIds = getCachedRecommendationIds(cacheKey);
        if (cachedIds != null && !cachedIds.isEmpty()) {
            return animeRepository.findAllById(cachedIds).stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        Set<Long> favoriteAnimeIds = favoriteRepository.findByUserId(userId).stream()
                .map(f -> f.getAnimeId())
                .collect(Collectors.toSet());
        Set<Long> watchedAnimeIds = watchHistoryRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(h -> h.getAnimeId())
                .collect(Collectors.toSet());
        Set<Long> userAnimeIds = new HashSet<>();
        userAnimeIds.addAll(favoriteAnimeIds);
        userAnimeIds.addAll(watchedAnimeIds);

        if (userAnimeIds.isEmpty()) {
            List<Anime> popular = getPopularAnime(limit);
            cacheRecommendationIds(cacheKey, popular.stream().map(Anime::getId).collect(Collectors.toList()));
            return popular;
        }

        Set<String> userGenres = getUserGenres(userAnimeIds);
        Set<Long> jikanRecommendations = getJikanRecommendations(userAnimeIds, 15);
        Set<Long> jikanAnimeIdsInDb = animeRepository.findAllById(jikanRecommendations).stream()
                .filter(a -> !userAnimeIds.contains(a.getId()))
                .map(Anime::getId)
                .collect(Collectors.toSet());
        List<Anime> genreBasedRecommendations = getGenreBasedRecommendationsV2(userGenres, userAnimeIds, 10);
        Set<Long> genreAnimeIds = genreBasedRecommendations.stream()
                .map(Anime::getId)
                .collect(Collectors.toSet());

        Set<Long> mergedAnimeIds = new HashSet<>();
        mergedAnimeIds.addAll(jikanAnimeIdsInDb);
        mergedAnimeIds.addAll(genreAnimeIds);

        if (mergedAnimeIds.size() < limit) {
            for (Anime anime : getPopularAnime(limit * 2)) {
                if (!mergedAnimeIds.contains(anime.getId()) && !userAnimeIds.contains(anime.getId())) {
                    mergedAnimeIds.add(anime.getId());
                    if (mergedAnimeIds.size() >= limit) break;
                }
            }
        }

        List<Long> shuffledIds = new ArrayList<>(mergedAnimeIds);
        Collections.shuffle(shuffledIds);
        shuffledIds = shuffledIds.stream().limit(limit).collect(Collectors.toList());

        List<Anime> finalRecommendations = animeRepository.findAllById(shuffledIds);
        cacheRecommendationIds(cacheKey, shuffledIds);
        return finalRecommendations;
    }

    public List<Anime> getSimilarAnime(Long animeId, int limit) {
        Optional<Anime> targetAnime = animeRepository.findById(animeId);
        if (targetAnime.isEmpty()) {
            return getPopularAnime(limit);
        }
        Anime anime = targetAnime.get();
        if (anime.getGenres() == null || anime.getGenres().isEmpty()) {
            return getPopularAnime(limit);
        }

        Set<String> targetGenreNames = anime.getGenres().stream()
                .map(Genre::getName)
                .filter(genreName -> genreName != null && !genreName.trim().isEmpty())
                .collect(Collectors.toSet());
        if (targetGenreNames.isEmpty()) {
            return getPopularAnime(limit);
        }

        int genreCount = targetGenreNames.size();
        int maxPerGenre = (int) Math.ceil((double) limit / genreCount);
        Set<Long> selectedAnimeIds = new HashSet<>();
        selectedAnimeIds.add(animeId);
        List<Anime> results = new ArrayList<>();

        for (String targetGenre : targetGenreNames) {
            List<Anime> genreResults = animeRepository.findTopByGenre(targetGenre, PageRequest.of(0, maxPerGenre));
            for (Anime result : genreResults) {
                if (!selectedAnimeIds.contains(result.getId()) && results.size() < limit) {
                    selectedAnimeIds.add(result.getId());
                    results.add(result);
                }
            }
            if (results.size() >= limit) break;
        }

        if (results.size() < limit) {
            for (Anime popularAnime : getPopularAnime(limit)) {
                if (!selectedAnimeIds.contains(popularAnime.getId())) {
                    results.add(popularAnime);
                    if (results.size() >= limit) break;
                }
            }
        }
        return results;
    }

    private void cacheRecommendationIds(String cacheKey, List<Long> animeIds) {
        try {
            String json = objectMapper.writeValueAsString(animeIds);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            // cache failure is non-fatal
        }
    }

    private List<Long> getCachedRecommendationIds(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
            }
        } catch (JsonProcessingException e) {
            // parse failure, recompute
        }
        return null;
    }

    public void invalidateUserRecommendations(Long userId) {
        redisTemplate.delete(String.format(REDIS_RECOMMENDATION_KEY, userId));
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
                                    if (recommendations.size() >= maxResults) return recommendations;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Jikan API call failed, skip
            }
        }
        return recommendations;
    }

    private List<Anime> getGenreBasedRecommendationsV2(Set<String> genres, Set<Long> excludeIds, int limit) {
        if (genres.isEmpty()) {
            return getPopularAnime(limit);
        }
        int genreCount = genres.size();
        int maxPerGenre = (int) Math.ceil((double) limit / genreCount);
        Set<Long> selectedAnimeIds = new HashSet<>(excludeIds);
        List<Anime> results = new ArrayList<>();

        for (String targetGenre : genres) {
            List<Anime> genreResults = animeRepository.findTopByGenre(targetGenre, PageRequest.of(0, maxPerGenre));
            for (Anime result : genreResults) {
                if (!selectedAnimeIds.contains(result.getId()) && results.size() < limit) {
                    selectedAnimeIds.add(result.getId());
                    results.add(result);
                }
            }
            if (results.size() >= limit) break;
        }

        if (results.size() < limit) {
            for (Anime popularAnime : getPopularAnime(limit * 2)) {
                if (!selectedAnimeIds.contains(popularAnime.getId())) {
                    results.add(popularAnime);
                    if (results.size() >= limit) break;
                }
            }
        }
        return results;
    }
}
