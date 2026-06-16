package com.anime.service;

import com.anime.dto.AnimeDto;
import com.anime.dto.DtoMapper;
import com.anime.entity.Anime;
import com.anime.repository.AnimeRepository;
import com.anime.repository.FavoriteRepository;
import com.anime.repository.RatingRepository;
import com.anime.repository.ReviewRepository;
    import com.anime.util.RedisUtil;
import org.springframework.data.domain.PageRequest;
    import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AnimeCacheService {

    private final RedisUtil redisUtil;
    private final AnimeRepository animeRepository;
    private final DtoMapper dtoMapper;
    private final FavoriteRepository favoriteRepository;
    private final ReviewRepository reviewRepository;
    private final RatingRepository ratingRepository;

    private static final String TRENDING_KEY = "anime:trending";
    private static final String TOP_RATED_KEY = "anime:top-rated";
    private static final String SEASONAL_KEY = "anime:seasonal";
    private static final int CACHE_DURATION_MINUTES = 5;

    public AnimeCacheService(RedisUtil redisUtil, AnimeRepository animeRepository, DtoMapper dtoMapper,
                             FavoriteRepository favoriteRepository, ReviewRepository reviewRepository,
                             RatingRepository ratingRepository) {
        this.redisUtil = redisUtil;
        this.animeRepository = animeRepository;
        this.dtoMapper = dtoMapper;
        this.favoriteRepository = favoriteRepository;
        this.reviewRepository = reviewRepository;
        this.ratingRepository = ratingRepository;
    }

    @SuppressWarnings("unchecked")
    public List<AnimeDto> getTrendingAnime(int limit) {
        String key = TRENDING_KEY + ":" + limit;
        Object cached = redisUtil.get(key);
        if (cached != null) {
            return (List<AnimeDto>) cached;
        }
        List<AnimeDto> result = fetchTrendingAnime(limit);
        redisUtil.set(key, result, CACHE_DURATION_MINUTES, TimeUnit.MINUTES);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<AnimeDto> getTopRatedAnime(int limit) {
        String key = TOP_RATED_KEY + ":" + limit;
        Object cached = redisUtil.get(key);
        if (cached != null) {
            return (List<AnimeDto>) cached;
        }
        List<AnimeDto> result = fetchTopRatedAnime(limit);
        redisUtil.set(key, result, CACHE_DURATION_MINUTES, TimeUnit.MINUTES);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<AnimeDto> getSeasonalAnime(String season, int limit) {
        String key = SEASONAL_KEY + ":" + (season != null ? season : "all") + ":" + limit;
        Object cached = redisUtil.get(key);
        if (cached != null) {
            return (List<AnimeDto>) cached;
        }
        List<AnimeDto> result = fetchSeasonalAnime(season, limit);
        redisUtil.set(key, result, CACHE_DURATION_MINUTES, TimeUnit.MINUTES);
        return result;
    }

    private List<AnimeDto> fetchTrendingAnime(int limit) {
        List<Anime> allAnime = animeRepository.findAll();
        if (allAnime == null || allAnime.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> favoriteCountMap = new HashMap<>();
        Map<Long, Long> reviewCountMap = new HashMap<>();
        Map<Long, Double> userRatingSumMap = new HashMap<>();
        Map<Long, Long> userRatingCountMap = new HashMap<>();

        List<Long> animeIds = animeRepository.findAllAnimeIds();
        if (animeIds != null && !animeIds.isEmpty()) {
            for (Long animeId : animeIds) {
                long favoriteCount = favoriteRepository.findByAnimeId(animeId).size();
                long reviewCount = reviewRepository.findByAnimeId(animeId).size();
                favoriteCountMap.put(animeId, favoriteCount);
                reviewCountMap.put(animeId, reviewCount);
            }

            List<com.anime.entity.Rating> allRatings = ratingRepository.findByAnimeIdIn(animeIds);
            for (Long animeId : animeIds) {
                List<com.anime.entity.Rating> animeRatings = allRatings.stream()
                        .filter(r -> animeId.equals(r.getAnimeId()))
                        .collect(Collectors.toList());
                long ratingCount = animeRatings.size();
                double ratingSum = animeRatings.stream().mapToDouble(com.anime.entity.Rating::getRating).sum();
                userRatingCountMap.put(animeId, ratingCount);
                userRatingSumMap.put(animeId, ratingSum);
            }
        }

        List<Anime> validAnime = allAnime.stream()
                .filter(a -> a.getScore() != null && a.getScore() > 0)
                .collect(Collectors.toList());

        if (validAnime.isEmpty()) {
            return List.of();
        }

        long maxFavorites = favoriteCountMap.values().stream().max(Long::compare).orElse(1L);
        long maxReviews = reviewCountMap.values().stream().max(Long::compare).orElse(1L);

        final double SCORE_WEIGHT = 0.35;
        final double USER_RATING_WEIGHT = 0.30;
        final double FAVORITE_WEIGHT = 0.20;
        final double REVIEW_WEIGHT = 0.15;

        Map<Anime, Double> trendingScores = new HashMap<>();
        for (Anime anime : validAnime) {
            double score = anime.getScore();
            long favoriteCount = favoriteCountMap.getOrDefault(anime.getId(), 0L);
            long reviewCount = reviewCountMap.getOrDefault(anime.getId(), 0L);
            long userRatingCount = userRatingCountMap.getOrDefault(anime.getId(), 0L);
            double userRatingSum = userRatingSumMap.getOrDefault(anime.getId(), 0.0);

            double normalizedFavorites = maxFavorites > 0 ? (double) favoriteCount / maxFavorites : 0;
            double normalizedReviews = maxReviews > 0 ? (double) reviewCount / maxReviews : 0;
        

            double avgUserRating = userRatingCount > 0 ? userRatingSum / userRatingCount : 0;
            double normalizedAvgUserRating = avgUserRating / 10.0;

            double trendingScore = (SCORE_WEIGHT * score) +
                                   (USER_RATING_WEIGHT * normalizedAvgUserRating * 10) +
                                   (FAVORITE_WEIGHT * normalizedFavorites * 10) +
                                   (REVIEW_WEIGHT * normalizedReviews * 10);

            trendingScores.put(anime, trendingScore);
        }

        return trendingScores.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .map(e -> dtoMapper.toAnimeDto(e.getKey()))
                .collect(Collectors.toList());
    }

    private List<AnimeDto> fetchTopRatedAnime(int limit) {
        return animeRepository.findTopRated(org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .map(dtoMapper::toAnimeDto)
                .collect(Collectors.toList());
    }

    private List<AnimeDto> fetchSeasonalAnime(String season, int limit) {
        // Use database-level filtering when season is specified
        if (season != null && !season.isEmpty()) {
            return animeRepository.findBySeason(season).stream()
                    .filter(a -> a.getScore() != null)
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(limit)
                    .map(dtoMapper::toAnimeDto)
                    .collect(Collectors.toList());
        }
        // No season filter: use top-rated as fallback with only scored anime
        return animeRepository.findTopRated(PageRequest.of(0, limit)).stream()
                .map(dtoMapper::toAnimeDto)
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRate = 300000)
    public void refreshAllCaches() {
        try {
            refreshTrendingCache();
            refreshTopRatedCache();
            refreshSeasonalCache();
        } catch (Exception e) {
            // Log the error but don't let it break the scheduler
            System.err.println("Error refreshing caches: " + e.getMessage());
        }
    }

    public void refreshTrendingCache() {
        List<Integer> limits = List.of(10);
        for (int limit : limits) {
            String key = TRENDING_KEY + ":" + limit;
            List<AnimeDto> data = fetchTrendingAnime(limit);
            redisUtil.set(key, data, CACHE_DURATION_MINUTES, TimeUnit.MINUTES);
        }
    }

    public void refreshTopRatedCache() {
        List<Integer> limits = List.of(10);
        for (int limit : limits) {
            String key = TOP_RATED_KEY + ":" + limit;
            List<AnimeDto> data = fetchTopRatedAnime(limit);
            redisUtil.set(key, data, CACHE_DURATION_MINUTES, TimeUnit.MINUTES);
        }
    }

    public void refreshSeasonalCache() {
        List<String> seasons = List.of("", "春季", "夏季", "秋季", "冬季");
        List<Integer> limits = List.of(10);
        for (String season : seasons) {
            for (int limit : limits) {
                String key = SEASONAL_KEY + ":" + season + ":" + limit;
                List<AnimeDto> data = fetchSeasonalAnime(season, limit);
                redisUtil.set(key, data, CACHE_DURATION_MINUTES, TimeUnit.MINUTES);
            }
        }
    }

    public void clearAllCaches() {
        deleteKeysIfExists("anime:trending:*");
        deleteKeysIfExists("anime:top-rated:*");
        deleteKeysIfExists("anime:recommendations:*");
        deleteKeysIfExists("anime:seasonal:*");
    }

    private void deleteKeysIfExists(String pattern) {
        try {
            java.util.Set<String> keys = redisUtil.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisUtil.delete(keys);
            }
        } catch (Exception e) {
            // Redis connection may not be available
        }
    }
}
