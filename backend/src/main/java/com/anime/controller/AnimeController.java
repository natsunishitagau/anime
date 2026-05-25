package com.anime.controller;

import com.anime.dto.ApiResponse;
import com.anime.dto.AnimeCharacterDto;
import com.anime.dto.AnimeDto;
import com.anime.dto.DtoMapper;
import com.anime.dto.ReviewDto;
import com.anime.dto.UserPrincipal;
import com.anime.entity.Anime;
import com.anime.entity.Character;
import com.anime.entity.AnimeCharacter;
import com.anime.entity.Favorite;
import com.anime.entity.Genre;
import com.anime.entity.Rating;
import com.anime.entity.Review;
import com.anime.entity.User;
import com.anime.repository.*;
import com.anime.service.AnimeCacheService;
import com.anime.service.RecommendationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/anime")
public class AnimeController {

    private final AnimeRepository animeRepository;
    private final CharacterRepository characterRepository;
    private final AnimeCharacterRepository animeCharacterRepository;
    private final ReviewRepository reviewRepository;
    private final FavoriteRepository favoriteRepository;
    private final RatingRepository ratingRepository;
    private final GenreRepository genreRepository;
    private final UserRepository userRepository;
    private final RecommendationService recommendationService;
    private final AnimeCacheService animeCacheService;
    private final DtoMapper dtoMapper;
    private final JdbcTemplate jdbcTemplate;

    public AnimeController(AnimeRepository animeRepository, CharacterRepository characterRepository,
                           AnimeCharacterRepository animeCharacterRepository, ReviewRepository reviewRepository,
                           FavoriteRepository favoriteRepository, RatingRepository ratingRepository,
                           GenreRepository genreRepository, UserRepository userRepository,
                           RecommendationService recommendationService, AnimeCacheService animeCacheService,
                           DtoMapper dtoMapper, JdbcTemplate jdbcTemplate) {
        this.animeRepository = animeRepository;
        this.characterRepository = characterRepository;
        this.animeCharacterRepository = animeCharacterRepository;
        this.reviewRepository = reviewRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.genreRepository = genreRepository;
        this.userRepository = userRepository;
        this.recommendationService = recommendationService;
        this.animeCacheService = animeCacheService;
        this.dtoMapper = dtoMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getAllAnime(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String source,
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
        if (source != null) {
            animeList = animeList.stream().filter(a -> source.equals(a.getSource())).collect(Collectors.toList());
        }
        if (season != null) {
            animeList = animeList.stream().filter(a -> season.equals(a.getSeason())).collect(Collectors.toList());
        }
        if (year != null) {
            animeList = animeList.stream().filter(a -> year.equals(a.getYear())).collect(Collectors.toList());
        }
        if (genre != null) {
            final String genreName = genre;
            animeList = animeList.stream()
                    .filter(a -> a.getGenres() != null &&
                            a.getGenres().stream()
                                    .anyMatch(g -> g.getName().equals(genreName)))
                    .collect(Collectors.toList());
        }
        if (status != null) {
            animeList = animeList.stream().filter(a -> status.equals(a.getStatus())).collect(Collectors.toList());
        }

        List<AnimeDto> dtoList = animeList.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .skip(offset)
                .limit(limit)
                .map(dtoMapper::toAnimeDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> searchAnime(@RequestParam String q) {
        if (q == null || q.length() < 2) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Search query must be at least 2 characters"));
        }

        List<Anime> results = animeRepository.searchAnime(q);
        List<AnimeDto> dtoList = results.stream()
                .map(dtoMapper::toAnimeDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/search/page")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchAnimePage(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (keyword == null || keyword.length() < 2) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Search query must be at least 2 characters"));
        }

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Anime> animePage = animeRepository.searchAnimePage(keyword, pageable);

        List<AnimeDto> dtoList = animePage.getContent().stream()
                .map(dtoMapper::toAnimeDto)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", dtoList);
        result.put("totalElements", animePage.getTotalElements());
        result.put("totalPages", animePage.getTotalPages());
        result.put("currentPage", page);
        result.put("pageSize", size);

        return ResponseEntity.ok(ApiResponse.success(result));
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

        List<AnimeCharacter> animeCharacters = animeCharacterRepository.findByAnimeId(id);
        List<Review> reviews = reviewRepository.findByAnimeIdOrderByCreatedAtDesc(id);
        List<Anime> similarAnime = recommendationService.getSimilarAnime(id, 8);

        List<AnimeCharacterDto> characterDtos = animeCharacters.stream()
                .map(ac -> {
                    Character character = characterRepository.findById(ac.getCharacterId()).orElse(null);
                    if (character == null) return null;
                    return new AnimeCharacterDto(
                            character.getId(),
                            character.getName(),
                            character.getNameJp(),
                            ac.getRole(),
                            character.getImageUrl(),
                            character.getFavorites()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("characters", characterDtos);
        List<ReviewDto> reviewDtos = reviews.stream().map(review -> {
            User user = userRepository.findById(review.getUserId()).orElse(null);
            String avatarUrl = user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : null;
            return new ReviewDto(
                    review.getId(),
                    review.getUserId(),
                    review.getUsername(),
                    avatarUrl,
                    review.getAnimeId(),
                    review.getComment(),
                    review.getCreatedAt() != null ? review.getCreatedAt().toString() : null
            );
        }).collect(Collectors.toList());
        response.put("reviews", reviewDtos);
        response.put("similarAnime", similarAnime.stream().map(dtoMapper::toAnimeDto).collect(Collectors.toList()));

        boolean isFavorited = false;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            Optional<Favorite> favorite = favoriteRepository.findByUserIdAndAnimeId(userPrincipal.getId(), id);
            isFavorited = favorite.isPresent();
        }
        response.put("isFavorited", isFavorited);

        response.put("anime", dtoMapper.toAnimeDto(anime));

        Integer userRating = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            Optional<Rating> rating = ratingRepository.findByUserIdAndAnimeId(userPrincipal.getId(), id);
            if (rating.isPresent()) {
                userRating = rating.get().getRating();
            }
        }
        response.put("userRating", userRating);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/genres")
    public ResponseEntity<?> getAnimeGenres(@PathVariable Long id) {
        String sql = "SELECT g.id, g.name FROM genres g " +
                     "JOIN anime_genre ag ON g.id = ag.genre_id " +
                     "WHERE ag.anime_id = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);

        String titleSql = "SELECT title FROM anime WHERE id = ?";
        String title = jdbcTemplate.queryForObject(titleSql, String.class, id);

        List<Map<String, Object>> genreList = rows.stream()
                .map(row -> Map.of("id", row.get("id"), "name", row.get("name")))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "animeId", id,
            "title", title != null ? title : "",
            "genresIsNull", false,
            "genresSize", genreList.size(),
            "genres", genreList
        ));
    }

    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getTrendingAnime(
            @RequestParam(defaultValue = "10") int limit) {
        List<AnimeDto> dtoList = animeCacheService.getTrendingAnime(limit);
        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/top-rated")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getTopRatedAnime(
            @RequestParam(defaultValue = "10") int limit) {
        List<AnimeDto> dtoList = animeCacheService.getTopRatedAnime(limit);
        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getRecommendations(
            @RequestParam(defaultValue = "10") int limit) {
        List<AnimeDto> dtoList = animeCacheService.getRecommendations(limit);
        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/seasonal")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getSeasonalAnime(
            @RequestParam(required = false) String season,
            @RequestParam(defaultValue = "10") int limit) {
        List<AnimeDto> dtoList = animeCacheService.getSeasonalAnime(season, limit);
        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/years")
    public ResponseEntity<ApiResponse<List<Integer>>> getDistinctYears() {
        List<Integer> years = animeRepository.findDistinctYears();
        return ResponseEntity.ok(ApiResponse.success(years));
    }

    @GetMapping("/genres")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllGenres() {
        List<Genre> genres = genreRepository.findAll();
        List<Map<String, Object>> genreList = genres.stream()
                .map(g -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", g.getId());
                    map.put("name", g.getName());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(genreList));
    }

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFilters() {
        List<Genre> genres = genreRepository.findAll();
        List<Integer> years = animeRepository.findDistinctYears();
        List<String> sources = animeRepository.findDistinctSources();

        List<String> seasons = List.of("春季", "夏季", "秋季", "冬季");
        List<String> types = List.of("TV动画", "剧场版", "OVA", "其他");
        List<String> statuses = List.of("连载中", "已完结");

        List<String> genreNames = genres.stream()
                .map(Genre::getName)
                .collect(Collectors.toList());

        Map<String, Object> filters = new HashMap<>();
        filters.put("genres", genreNames);
        filters.put("seasons", seasons);
        filters.put("years", years);
        filters.put("types", types);
        filters.put("statuses", statuses);
        filters.put("sources", sources);

        return ResponseEntity.ok(ApiResponse.success(filters));
    }

    @PostMapping("/{id}/rate")
    public ResponseEntity<ApiResponse<Void>> rateAnime(
            @PathVariable Long id,
            @RequestParam Integer rating,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        Optional<Rating> existingRating = ratingRepository.findByUserIdAndAnimeId(userPrincipal.getId(), id);
        if (existingRating.isPresent()) {
            Rating ratingEntity = existingRating.get();
            ratingEntity.setRating(rating);
            ratingRepository.save(ratingEntity);
        } else {
            Rating ratingEntity = new Rating();
            ratingEntity.setUserId(userPrincipal.getId());
            ratingEntity.setAnimeId(id);
            ratingEntity.setRating(rating);
            ratingRepository.save(ratingEntity);
        }

        return ResponseEntity.ok(ApiResponse.success("Rating saved", null));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<Void>> addReview(
            @PathVariable Long id,
            @RequestParam(required = false) String comment,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        Review review = new Review();
        review.setUserId(userPrincipal.getId());
        review.setAnimeId(id);
        review.setComment(comment);
        review.setUsername(userPrincipal.getUsername());
        reviewRepository.save(review);

        return ResponseEntity.ok(ApiResponse.success("Review added", null));
    }
}
