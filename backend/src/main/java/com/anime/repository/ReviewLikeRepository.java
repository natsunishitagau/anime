package com.anime.repository;

import com.anime.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    Optional<ReviewLike> findByUserIdAndReviewId(Long userId, Long reviewId);
    boolean existsByUserIdAndReviewId(Long userId, Long reviewId);
    void deleteByUserIdAndReviewId(Long userId, Long reviewId);
    List<ReviewLike> findByReviewId(Long reviewId);
}