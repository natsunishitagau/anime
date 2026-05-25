package com.anime.repository;

import com.anime.entity.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    
    @Query("SELECT w FROM WatchHistory w WHERE w.userId = :userId ORDER BY w.updatedAt DESC")
    List<WatchHistory> findByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);
    
    Optional<WatchHistory> findByUserIdAndAnimeId(Long userId, Long animeId);
    
    Optional<WatchHistory> findByUserIdAndAnimeIdAndEpisodeId(Long userId, Long animeId, Long episodeId);
    
    List<WatchHistory> findByUserIdAndAnimeIdOrderByEpisodeNumberAsc(Long userId, Long animeId);
}