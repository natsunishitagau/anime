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
import com.anime.entity.ReviewLike;
import com.anime.entity.User;
import com.anime.repository.*;
import com.anime.service.AnimeCacheService;
import com.anime.service.MessageService;
import com.anime.service.RecommendationService;
import com.anime.service.SensitiveWordFilter;
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
    private final ReviewLikeRepository reviewLikeRepository;
    private final FavoriteRepository favoriteRepository;
    private final RatingRepository ratingRepository;
    private final GenreRepository genreRepository;
    private final UserRepository userRepository;
    private final RecommendationService recommendationService;
    private final AnimeCacheService animeCacheService;
    private final DtoMapper dtoMapper;
    private final JdbcTemplate jdbcTemplate;
    private final MessageService messageService;
    private final SensitiveWordFilter sensitiveWordFilter;

    public AnimeController(AnimeRepository animeRepository, CharacterRepository characterRepository,
                           AnimeCharacterRepository animeCharacterRepository, ReviewRepository reviewRepository,
                           ReviewLikeRepository reviewLikeRepository, FavoriteRepository favoriteRepository,
                           RatingRepository ratingRepository, GenreRepository genreRepository, UserRepository userRepository,
                           RecommendationService recommendationService, AnimeCacheService animeCacheService,
                           DtoMapper dtoMapper, JdbcTemplate jdbcTemplate, MessageService messageService,
                           SensitiveWordFilter sensitiveWordFilter) {
        this.animeRepository = animeRepository;
        this.characterRepository = characterRepository;
        this.animeCharacterRepository = animeCharacterRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLikeRepository = reviewLikeRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.genreRepository = genreRepository;
        this.userRepository = userRepository;
        this.recommendationService = recommendationService;
        this.animeCacheService = animeCacheService;
        this.dtoMapper = dtoMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.messageService = messageService;
        this.sensitiveWordFilter = sensitiveWordFilter;
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
            if ("其他".equals(type)) {
                animeList = animeList.stream()
                        .filter(a -> !"TV动画".equals(a.getType()) && 
                                    !"剧场版".equals(a.getType()) && 
                                    !"OVA".equals(a.getType()))
                        .collect(Collectors.toList());
            } else {
                animeList = animeList.stream().filter(a -> type.equals(a.getType())).collect(Collectors.toList());
            }
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
            return ResponseEntity.badRequest().body(ApiResponse.error("关键词不能少于2个字符"));
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
            return ResponseEntity.badRequest().body(ApiResponse.error("关键词不能少于2个字符"));
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
        
        final Long currentUserId = (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) 
            ? ((UserPrincipal) authentication.getPrincipal()).getId() 
            : null;
        
        List<ReviewDto> reviewDtos = reviews.stream().map(review -> {
            User user = userRepository.findById(review.getUserId()).orElse(null);
            String avatarUrl = user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : null;
            Boolean liked = false;
            if (currentUserId != null) {
                liked = reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, review.getId());
            }
            return new ReviewDto(
                    review.getId(),
                    review.getUserId(),
                    review.getUsername(),
                    avatarUrl,
                    review.getAnimeId(),
                    review.getComment(),
                    review.getCreatedAt() != null ? review.getCreatedAt().toString() : null,
                    review.getLikes(),
                    liked,
                    review.getTopLevelId(),
                    review.getParentId(),
                    review.getIsDeleted()
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
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        // 如果用户未登录，返回空列表（不显示为你推荐）
        if (authentication == null) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }
        
        // 用户已登录，获取个性化推荐
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        List<Anime> animeList = recommendationService.getRecommendations(userPrincipal.getId(), limit);
        List<AnimeDto> dtoList = animeList.stream()
                .map(dtoMapper::toAnimeDto)
                .collect(Collectors.toList());
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
            @RequestParam(required = false) Long parentId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String filteredComment = sensitiveWordFilter.filter(comment);

        Review review = new Review();
        review.setUserId(userPrincipal.getId());
        review.setAnimeId(id);
        review.setComment(filteredComment);
        review.setUsername(userPrincipal.getUsername());
        
        if (parentId != null) {
            Optional<Review> parentReview = reviewRepository.findById(parentId);
            if (parentReview.isPresent()) {
                review.setParentId(parentId);
                review.setTopLevelId(parentReview.get().getTopLevelId() != null ? parentReview.get().getTopLevelId() : parentId);
                
                Review parent = parentReview.get();
                messageService.sendReviewReplyNotification(
                    parent.getUserId(),
                    userPrincipal.getId(),
                    review.getId(),
                    userPrincipal.getUsername(),
                    comment
                );
            }
        }
        
        reviewRepository.save(review);

        return ResponseEntity.ok(ApiResponse.success("Review added", null));
    }

    @PostMapping("/{id}/review/{reviewId}/like")
    public ResponseEntity<ApiResponse<Map<String, Object>>> likeReview(
            @PathVariable Long id,
            @PathVariable Long reviewId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Review not found"));
        }

        Review review = reviewOpt.get();
        Optional<ReviewLike> existingLike = reviewLikeRepository.findByUserIdAndReviewId(userPrincipal.getId(), reviewId);
        
        Map<String, Object> result = new HashMap<>();
        
        if (existingLike.isPresent()) {
            reviewLikeRepository.delete(existingLike.get());
            review.setLikes(review.getLikes() != null ? review.getLikes() - 1 : 0);
            result.put("liked", false);
            
            messageService.cancelReviewLikeNotification(
                review.getUserId(),
                userPrincipal.getId(),
                reviewId
            );
        } else {
            ReviewLike reviewLike = new ReviewLike();
            reviewLike.setUserId(userPrincipal.getId());
            reviewLike.setReviewId(reviewId);
            reviewLikeRepository.save(reviewLike);
            review.setLikes(review.getLikes() != null ? review.getLikes() + 1 : 1);
            result.put("liked", true);
            
            messageService.sendReviewLikeNotification(
                review.getUserId(),
                userPrincipal.getId(),
                reviewId,
                userPrincipal.getUsername(),
                review.getComment()
            );
        }
        
        reviewRepository.save(review);
        result.put("likes", review.getLikes());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}/review/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long id,
            @PathVariable Long reviewId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        
        if (reviewOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Review not found"));
        }

        Review review = reviewOpt.get();
        if (!review.getUserId().equals(userPrincipal.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("Unauthorized to delete this review"));
        }

        review.setIsDeleted(true);
        review.setComment("评论已删除");
        reviewRepository.save(review);

        return ResponseEntity.ok(ApiResponse.success("Review deleted", null));
    }

    @GetMapping("/{id}/reviews/tree")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getReviewsTree(
            @PathVariable Long id,
            Authentication authentication) {
        
        final Long currentUserId = (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) 
            ? ((UserPrincipal) authentication.getPrincipal()).getId() 
            : null;
        
        List<Review> allReviews = reviewRepository.findByAnimeIdAndIsDeletedFalseOrderByCreatedAtDesc(id);
        
        List<ReviewDto> reviewDtos = allReviews.stream().map(review -> {
            User user = userRepository.findById(review.getUserId()).orElse(null);
            String avatarUrl = user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : null;
            Boolean liked = false;
            if (currentUserId != null) {
                liked = reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, review.getId());
            }
            return new ReviewDto(
                    review.getId(),
                    review.getUserId(),
                    review.getUsername(),
                    avatarUrl,
                    review.getAnimeId(),
                    review.getComment(),
                    review.getCreatedAt() != null ? review.getCreatedAt().toString() : null,
                    review.getLikes(),
                    liked,
                    review.getTopLevelId(),
                    review.getParentId(),
                    review.getIsDeleted()
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(reviewDtos));
    }

    @PostMapping("/game-sequence")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getGameSequence(
            @RequestBody GameSequenceRequest request) {
        
        Integer startYear = request.getStartYear();
        Integer endYear = request.getEndYear();
        String typeFilter = request.getTypeFilter();
        int count = request.getCount();

        List<Anime> allAnime = animeRepository.findAll();

        List<Anime> filtered = allAnime.stream()
                .filter(anime -> {
                    if (anime.getScore() == null || anime.getScore() == 0) {
                        return false;
                    }
                    if (anime.getImageUrl() == null || anime.getImageUrl().isEmpty()) {
                        return false;
                    }
                    if (anime.getTitleJp() == null || anime.getTitleJp().isEmpty()) {
                        return false;
                    }
                    return true;
                })
                .filter(anime -> {
                    if (startYear == null && endYear == null) {
                        return true;
                    }
                    if (anime.getYear() == null) {
                        return false;
                    }
                    boolean afterStart = startYear == null || anime.getYear() >= startYear;
                    boolean beforeEnd = endYear == null || anime.getYear() <= endYear;
                    return afterStart && beforeEnd;
                })
                .filter(anime -> {
                    if ("all".equals(typeFilter)) {
                        return true;
                    }
                    return typeFilter.equals(anime.getType());
                })
                .collect(Collectors.toList());

        List<Anime> deduplicated = deduplicateByTitleJpPrefix(filtered);

        Map<Double, Anime> uniqueScoreMap = deduplicated.stream()
                .filter(a -> a.getScore() != null)
                .collect(Collectors.toMap(
                        Anime::getScore,
                        a -> a,
                        (existing, replacement) -> existing
                ));

        List<Anime> uniqueScoreList = new ArrayList<>(uniqueScoreMap.values());

        if (uniqueScoreList.size() < count) {
            return ResponseEntity.ok(ApiResponse.success(new ArrayList<>()));
        }

        Collections.shuffle(uniqueScoreList);

        List<AnimeDto> result = uniqueScoreList.stream()
                .limit(count)
                .map(dtoMapper::toAnimeDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private List<Anime> deduplicateByTitleJpPrefix(List<Anime> animes) {
        List<Anime> sorted = animes.stream()
                .filter(a -> a.getTitleJp() != null && !a.getTitleJp().isEmpty())
                .sorted(Comparator.comparingInt(a -> a.getTitleJp().length()))
                .collect(Collectors.toList());

        Set<String> seenPrefixes = new HashSet<>();
        List<Anime> result = new ArrayList<>();

        for (Anime anime : sorted) {
            String titleJp = anime.getTitleJp();
            boolean isSubset = false;

            for (String seen : seenPrefixes) {
                if (titleJp.startsWith(seen) || seen.startsWith(titleJp)) {
                    isSubset = true;
                    break;
                }
            }

            if (!isSubset) {
                result.add(anime);
                seenPrefixes.add(titleJp);
            }
        }

        return result;
    }

    public static class GameSequenceRequest {
        private Integer startYear;
        private Integer endYear;
        private String typeFilter;
        private int count;

        public Integer getStartYear() {
            return startYear;
        }

        public void setStartYear(Integer startYear) {
            this.startYear = startYear;
        }

        public Integer getEndYear() {
            return endYear;
        }

        public void setEndYear(Integer endYear) {
            this.endYear = endYear;
        }

        public String getTypeFilter() {
            return typeFilter;
        }

        public void setTypeFilter(String typeFilter) {
            this.typeFilter = typeFilter;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
