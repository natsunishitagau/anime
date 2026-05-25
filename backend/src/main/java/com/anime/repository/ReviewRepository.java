package com.anime.repository;

import com.anime.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByAnimeIdOrderByCreatedAtDesc(Long animeId);
    List<Review> findRecentReviewsByAnimeId(Long animeId);
    List<Review> findByAnimeId(Long animeId);
}