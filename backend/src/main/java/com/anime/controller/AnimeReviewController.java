package com.anime.controller;

import com.anime.dto.ReviewDto;
import com.anime.dto.UserPrincipal;
import com.anime.dto.response.ApiResponse;
import com.anime.entity.Rating;
import com.anime.entity.Review;
import com.anime.entity.ReviewLike;
import com.anime.entity.User;
import com.anime.repository.*;
import com.anime.service.MessageService;
import com.anime.util.SensitiveWordFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/anime")
public class AnimeReviewController {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final SensitiveWordFilter sensitiveWordFilter;

    public AnimeReviewController(ReviewRepository reviewRepository, ReviewLikeRepository reviewLikeRepository,
                                 RatingRepository ratingRepository, UserRepository userRepository,
                                 MessageService messageService, SensitiveWordFilter sensitiveWordFilter) {
        this.reviewRepository = reviewRepository;
        this.reviewLikeRepository = reviewLikeRepository;
        this.ratingRepository = ratingRepository;
        this.userRepository = userRepository;
        this.messageService = messageService;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    @PostMapping("/{id}/rate")
    public ResponseEntity<ApiResponse<Void>> rateAnime(
            @PathVariable Long id, @RequestParam Integer rating, Authentication authentication) {
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
            @PathVariable Long id, @RequestParam(required = false) String comment,
            @RequestParam(required = false) Long parentId, Authentication authentication) {
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
                    parent.getUserId(), userPrincipal.getId(), review.getId(),
                    userPrincipal.getUsername(), comment);
            }
        }
        reviewRepository.save(review);
        return ResponseEntity.ok(ApiResponse.success("Review added", null));
    }

    @PostMapping("/{id}/review/{reviewId}/like")
    public ResponseEntity<ApiResponse<Map<String, Object>>> likeReview(
            @PathVariable Long id, @PathVariable Long reviewId, Authentication authentication) {
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
            messageService.cancelReviewLikeNotification(review.getUserId(), userPrincipal.getId(), reviewId);
        } else {
            ReviewLike reviewLike = new ReviewLike();
            reviewLike.setUserId(userPrincipal.getId());
            reviewLike.setReviewId(reviewId);
            reviewLikeRepository.save(reviewLike);
            review.setLikes(review.getLikes() != null ? review.getLikes() + 1 : 1);
            result.put("liked", true);
            messageService.sendReviewLikeNotification(review.getUserId(), userPrincipal.getId(), reviewId,
                userPrincipal.getUsername(), review.getComment());
        }
        reviewRepository.save(review);
        result.put("likes", review.getLikes());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}/review/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long id, @PathVariable Long reviewId, Authentication authentication) {
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
        review.setComment("\u8bc4\u8bba\u5df2\u5220\u9664");
        reviewRepository.save(review);
        return ResponseEntity.ok(ApiResponse.success("Review deleted", null));
    }

    @GetMapping("/{id}/reviews/tree")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getReviewsTree(
            @PathVariable Long id, Authentication authentication) {
        final Long currentUserId = (authentication != null && authentication.getPrincipal() instanceof UserPrincipal)
            ? ((UserPrincipal) authentication.getPrincipal()).getId() : null;
        List<Review> allReviews = reviewRepository.findByAnimeIdAndIsDeletedFalseOrderByCreatedAtDesc(id);
        List<ReviewDto> reviewDtos = allReviews.stream().map(review -> {
            User user = userRepository.findById(review.getUserId()).orElse(null);
            String avatarUrl = user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : null;
            Boolean liked = false;
            if (currentUserId != null) {
                liked = reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, review.getId());
            }
            return new ReviewDto(review.getId(), review.getUserId(), review.getUsername(), avatarUrl,
                    review.getAnimeId(), review.getComment(),
                    review.getCreatedAt() != null ? review.getCreatedAt().toString() : null,
                    review.getLikes(), liked, review.getTopLevelId(), review.getParentId(), review.getIsDeleted());
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(reviewDtos));
    }
}
