package com.anime.controller;

import com.anime.dto.ApiResponse;
import com.anime.dto.AnimeDto;
import com.anime.dto.DtoMapper;
import com.anime.dto.ReviewDto;
import com.anime.dto.UserPrincipal;
import com.anime.entity.Anime;
import com.anime.entity.Character;
import com.anime.entity.Favorite;
import com.anime.entity.Rating;
import com.anime.entity.Review;
import com.anime.repository.*;
import com.anime.service.RecommendationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/anime")
public class AnimeController {

    private final AnimeRepository animeRepository;
    private final CharacterRepository characterRepository;
    private final ReviewRepository reviewRepository;
    private final FavoriteRepository favoriteRepository;
    private final RatingRepository ratingRepository;
    private final RecommendationService recommendationService;

    public AnimeController(AnimeRepository animeRepository, CharacterRepository characterRepository,
                           ReviewRepository reviewRepository, FavoriteRepository favoriteRepository,
                           RatingRepository ratingRepository, RecommendationService recommendationService) {
        this.animeRepository = animeRepository;
        this.characterRepository = characterRepository;
        this.reviewRepository = reviewRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getAllAnime(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        List<Anime> animeList = animeRepository.findAll();
        
        if (type != null) {
            animeList = animeList.stream().filter(a -> type.equals(a.getType())).collect(Collectors.toList());
        }
        if (season != null) {
            animeList = animeList.stream().filter(a -> season.equals(a.getSeason())).collect(Collectors.toList());
        }
        if (year != null) {
            animeList = animeList.stream().filter(a -> year.equals(a.getYear())).collect(Collectors.toList());
        }
        if (genre != null) {
            animeList = animeList.stream().filter(a -> a.getGenres() != null && a.getGenres().contains(genre)).collect(Collectors.toList());
        }
        if (status != null) {
            animeList = animeList.stream().filter(a -> status.equals(a.getStatus())).collect(Collectors.toList());
        }
        
        List<AnimeDto> dtoList = animeList.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .skip(offset)
                .limit(limit)
                .map(DtoMapper::toAnimeDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> searchAnime(@RequestParam String q) {
        if (q == null || q.length() < 2) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Search query must be at least 2 characters"));
        }
        List<Anime> results = animeRepository.searchAnime(q);
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toAnimeDtoList(results)));
    }

    @GetMapping("/genres")
    public ResponseEntity<ApiResponse<List<String>>> getGenres() {
        Set<String> genreSet = new LinkedHashSet<>();
        animeRepository.findAll().forEach(anime -> {
            if (anime.getGenres() != null) {
                Arrays.stream(anime.getGenres().split(","))
                        .map(String::trim)
                        .forEach(genreSet::add);
            }
        });
        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>(genreSet)));
    }

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFilters() {
        Map<String, Object> filters = new HashMap<>();
        
        Set<String> genreSet = new LinkedHashSet<>();
        animeRepository.findAll().forEach(anime -> {
            if (anime.getGenres() != null) {
                Arrays.stream(anime.getGenres().split(","))
                        .map(String::trim)
                        .forEach(genreSet::add);
            }
        });
        filters.put("genres", new ArrayList<>(genreSet));
        filters.put("seasons", Arrays.asList("春季", "夏季", "秋季", "冬季"));
        filters.put("years", animeRepository.findDistinctYears());
        filters.put("types", Arrays.asList("TV", "MOVIE", "OVA", "ONA", "SPECIAL"));
        filters.put("statuses", Arrays.asList("播出中", "已完结", "即将播出"));
        
        return ResponseEntity.ok(ApiResponse.success(filters));
    }

    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getTrending(@RequestParam(defaultValue = "10") int limit) {
        List<Anime> trending = recommendationService.getPopularAnime(limit);
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toAnimeDtoList(trending)));
    }

    @GetMapping("/top-rated")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getTopRated(@RequestParam(defaultValue = "20") int limit) {
        List<Anime> topRated = animeRepository.findTopRated(PageRequest.of(0, limit));
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toAnimeDtoList(topRated)));
    }

    @GetMapping("/seasonal")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getSeasonal(
            @RequestParam(required = false) String season,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "20") int limit) {
        List<Anime> seasonal = recommendationService.getSeasonalAnime(season, year, limit);
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toAnimeDtoList(seasonal)));
    }

    @GetMapping("/by-genre/{genre}")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getByGenre(
            @PathVariable String genre,
            @RequestParam(defaultValue = "20") int limit) {
        List<Anime> anime = recommendationService.getAnimeByGenre(genre, limit);
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toAnimeDtoList(anime)));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRecommendations(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (authentication == null) {
            response.put("anime", DtoMapper.toAnimeDtoList(recommendationService.getPopularAnime(limit)));
            response.put("personalized", false);
        } else {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            response.put("anime", DtoMapper.toAnimeDtoList(recommendationService.getRecommendations(userPrincipal.getId(), limit)));
            response.put("personalized", true);
        }
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/collaborative")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getCollaborative(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        List<Anime> recommendations = recommendationService.getCollaborativeRecommendations(userPrincipal.getId(), limit);
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toAnimeDtoList(recommendations)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnimeById(
            @PathVariable Long id,
            Authentication authentication) {
        
        Optional<Anime> animeOpt = animeRepository.findById(id);
        if (animeOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Anime not found"));
        }

        Anime anime = animeOpt.get();
        List<Character> characters = characterRepository.findByAnimeId(id);
        List<Review> reviews = reviewRepository.findByAnimeIdOrderByCreatedAtDesc(id);
        List<Anime> similarAnime = recommendationService.getSimilarAnime(id, 8);

        Map<String, Object> response = new HashMap<>();
        response.put("anime", DtoMapper.toAnimeDto(anime));
        response.put("characters", DtoMapper.toCharacterDtoList(characters));
        response.put("reviews", DtoMapper.toReviewDtoList(reviews));
        response.put("similarAnime", DtoMapper.toAnimeDtoList(similarAnime));

        if (authentication != null) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            response.put("isFavorited", favoriteRepository.existsByUserIdAndAnimeId(userPrincipal.getId(), id));
            Optional<Rating> userRating = ratingRepository.findByUserIdAndAnimeId(userPrincipal.getId(), id);
            response.put("userRating", userRating.map(Rating::getRating).orElse(null));
        } else {
            response.put("isFavorited", false);
            response.put("userRating", null);
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getSimilarAnime(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {
        List<Anime> similar = recommendationService.getSimilarAnime(id, limit);
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toAnimeDtoList(similar)));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getReviews(
            @PathVariable Long id) {
        List<Review> reviews = reviewRepository.findRecentReviewsByAnimeId(id);
        return ResponseEntity.ok(ApiResponse.success(DtoMapper.toReviewDtoList(reviews)));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<ApiResponse<Void>> toggleFavorite(
            @PathVariable Long id,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<Anime> animeOpt = animeRepository.findById(id);
        
        if (animeOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Anime not found"));
        }

        Optional<Favorite> existing = favoriteRepository.findByUserIdAndAnimeId(userPrincipal.getId(), id);
        
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return ResponseEntity.ok(ApiResponse.success("Removed from favorites", null));
        } else {
            Favorite favorite = new Favorite();
            favorite.setUserId(userPrincipal.getId());
            favorite.setAnimeId(id);
            favoriteRepository.save(favorite);
            return ResponseEntity.ok(ApiResponse.success("Added to favorites", null));
        }
    }

    @PostMapping("/{id}/rate")
    public ResponseEntity<ApiResponse<Void>> rateAnime(
            @PathVariable Long id,
            @RequestParam int rating,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        if (rating < 1 || rating > 10) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Rating must be between 1 and 10"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<Anime> animeOpt = animeRepository.findById(id);
        
        if (animeOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Anime not found"));
        }

        Optional<Rating> existing = ratingRepository.findByUserIdAndAnimeId(userPrincipal.getId(), id);
        
        Rating ratingEntity = existing.orElse(new Rating());
        ratingEntity.setUserId(userPrincipal.getId());
        ratingEntity.setAnimeId(id);
        ratingEntity.setRating(rating);
        ratingRepository.save(ratingEntity);

        return ResponseEntity.ok(ApiResponse.success("Rating saved", null));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<Review>> addReview(
            @PathVariable Long id,
            @RequestParam int rating,
            @RequestParam(required = false) String comment,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        if (rating < 1 || rating > 10) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Rating must be between 1 and 10"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<Anime> animeOpt = animeRepository.findById(id);
        
        if (animeOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Anime not found"));
        }

        Rating ratingEntity = new Rating();
        ratingEntity.setUserId(userPrincipal.getId());
        ratingEntity.setAnimeId(id);
        ratingEntity.setRating(rating);
        ratingRepository.save(ratingEntity);

        Review review = new Review();
        review.setUserId(userPrincipal.getId());
        review.setAnimeId(id);
        review.setRating(rating);
        review.setComment(comment);
        Review savedReview = reviewRepository.save(review);
        savedReview.setUsername(userPrincipal.getUsername());

        return ResponseEntity.ok(ApiResponse.success("Review added", savedReview));
    }
}