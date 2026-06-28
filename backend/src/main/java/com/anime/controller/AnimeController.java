package com.anime.controller;

import com.anime.dto.AnimeCharacterDto;
import com.anime.dto.AnimeDto;
import com.anime.dto.DtoMapper;
import com.anime.dto.UserPrincipal;
import com.anime.dto.response.ApiResponse;
import com.anime.entity.Anime;
import com.anime.entity.AnimeCharacter;
import com.anime.entity.Character;
import com.anime.entity.Favorite;
import com.anime.entity.Genre;
import com.anime.entity.Rating;
import com.anime.repository.*;
import com.anime.service.AnimeCacheService;
import com.anime.service.RecommendationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private final GenreRepository genreRepository;
    private final FavoriteRepository favoriteRepository;
    private final RecommendationService recommendationService;
    private final AnimeCacheService animeCacheService;
    private final DtoMapper dtoMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RatingRepository ratingRepository;

    public AnimeController(AnimeRepository animeRepository, CharacterRepository characterRepository,
                           AnimeCharacterRepository animeCharacterRepository,
                           GenreRepository genreRepository,
                           FavoriteRepository favoriteRepository,
                           RecommendationService recommendationService, AnimeCacheService animeCacheService,
                           DtoMapper dtoMapper, JdbcTemplate jdbcTemplate,
                           RatingRepository ratingRepository) {
        this.animeRepository = animeRepository;
        this.characterRepository = characterRepository;
        this.animeCharacterRepository = animeCharacterRepository;
        this.genreRepository = genreRepository;
        this.favoriteRepository = favoriteRepository;
        this.recommendationService = recommendationService;
        this.animeCacheService = animeCacheService;
        this.dtoMapper = dtoMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.ratingRepository = ratingRepository;
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

        Specification<Anime> spec = Specification.where(null);
        if (type != null) spec = spec.and(AnimeSpecifications.hasType(type));
        if (source != null) spec = spec.and(AnimeSpecifications.hasSource(source));
        if (season != null) spec = spec.and(AnimeSpecifications.hasSeason(season));
        if (year != null) spec = spec.and(AnimeSpecifications.hasYear(year));
        if (genre != null) spec = spec.and(AnimeSpecifications.hasGenre(genre));
        if (status != null) spec = spec.and(AnimeSpecifications.hasStatus(status));

        List<Anime> animeList = animeRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "score"));

        List<AnimeDto> dtoList = animeList.stream()
                .skip(offset)
                .limit(limit)
                .map(dtoMapper::toAnimeDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> searchAnime(@RequestParam String q) {
        if (q == null || q.length() < 2) {
            return ResponseEntity.badRequest().body(ApiResponse.error("\u5173\u952e\u8bcd\u4e0d\u80fd\u5c11\u4e8e2\u4e2a\u5b57\u7b26"));
        }
        List<Anime> results = animeRepository.searchAnime(q);
        List<AnimeDto> dtoList = results.stream().map(dtoMapper::toAnimeDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/search/page")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchAnimePage(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (keyword == null || keyword.length() < 2) {
            return ResponseEntity.badRequest().body(ApiResponse.error("\u5173\u952e\u8bcd\u4e0d\u80fd\u5c11\u4e8e2\u4e2a\u5b57\u7b26"));
        }
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Anime> animePage = animeRepository.searchAnimePage(keyword, pageable);
        List<AnimeDto> dtoList = animePage.getContent().stream().map(dtoMapper::toAnimeDto).collect(Collectors.toList());
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
            @PathVariable Long id, Authentication authentication) {
        Optional<Anime> animeOpt = animeRepository.findById(id);
        if (animeOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Anime not found"));
        }
        Anime anime = animeOpt.get();
        List<AnimeCharacter> animeCharacters = animeCharacterRepository.findByAnimeId(id);
        List<Anime> similarAnime = recommendationService.getSimilarAnime(id, 8);
        List<AnimeCharacterDto> characterDtos = animeCharacters.stream()
                .map(ac -> {
                    Character character = characterRepository.findById(ac.getCharacterId()).orElse(null);
                    if (character == null) return null;
                    return new AnimeCharacterDto(character.getId(), character.getName(), character.getNameJp(),
                            ac.getRole(), character.getImageUrl(), character.getFavorites());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("characters", characterDtos);
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
            Optional<Rating> existing = ratingRepository.findByUserIdAndAnimeId(userPrincipal.getId(), id);
            if (existing.isPresent()) {
                userRating = existing.get().getRating();
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
            "animeId", id, "title", title != null ? title : "",
            "genresIsNull", false, "genresSize", genreList.size(), "genres", genreList));
    }

    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getTrendingAnime(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(animeCacheService.getTrendingAnime(limit)));
    }

    @GetMapping("/top-rated")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getTopRatedAnime(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(animeCacheService.getTopRatedAnime(limit)));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getRecommendations(
            @RequestParam(defaultValue = "10") int limit, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        List<Anime> animeList = recommendationService.getRecommendations(userPrincipal.getId(), limit);
        List<AnimeDto> dtoList = animeList.stream().map(dtoMapper::toAnimeDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtoList));
    }

    @GetMapping("/seasonal")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getSeasonalAnime(
            @RequestParam(required = false) String season,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(animeCacheService.getSeasonalAnime(season, limit)));
    }

    @GetMapping("/years")
    public ResponseEntity<ApiResponse<List<Integer>>> getDistinctYears() {
        return ResponseEntity.ok(ApiResponse.success(animeRepository.findDistinctYears()));
    }

    @GetMapping("/genres")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllGenres() {
        List<Genre> genres = genreRepository.findAll();
        List<Map<String, Object>> genreList = genres.stream()
                .map(g -> { Map<String, Object> map = new HashMap<>(); map.put("id", g.getId()); map.put("name", g.getName()); return map; })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(genreList));
    }

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFilters() {
        List<Genre> genres = genreRepository.findAll();
        List<Integer> years = animeRepository.findDistinctYears();
        List<String> sources = animeRepository.findDistinctSources();
        List<String> seasons = List.of("\u6625\u5b63", "\u590f\u5b63", "\u79cb\u5b63", "\u51ac\u5b63");
        List<String> types = List.of("TV\u52a8\u753b", "\u5267\u573a\u7248", "OVA", "\u5176\u4ed6");
        List<String> statuses = List.of("\u8fde\u8f7d\u4e2d", "\u5df2\u5b8c\u7ed3");
        List<String> genreNames = genres.stream().map(Genre::getName).collect(Collectors.toList());
        Map<String, Object> filters = new HashMap<>();
        filters.put("genres", genreNames);
        filters.put("seasons", seasons);
        filters.put("years", years);
        filters.put("types", types);
        filters.put("statuses", statuses);
        filters.put("sources", sources);
        return ResponseEntity.ok(ApiResponse.success(filters));
    }
}
