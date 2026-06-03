package com.anime.repository;

import com.anime.entity.Danmaku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DanmakuRepository extends JpaRepository<Danmaku, Long> {
    List<Danmaku> findByVideoIdAndIsDeletedOrderByTimeAsc(Long videoId, Boolean isDeleted);

    @Query("SELECT d FROM Danmaku d WHERE d.videoId = :videoId AND d.isDeleted = false AND d.time >= :startTime AND d.time < :endTime ORDER BY d.time ASC")
    List<Danmaku> findByVideoIdAndTimeRange(@Param("videoId") Long videoId, @Param("startTime") Double startTime, @Param("endTime") Double endTime);

    @Query("SELECT d FROM Danmaku d WHERE d.videoId = :videoId AND d.isDeleted = false AND d.time >= :startTime ORDER BY d.time ASC")
    List<Danmaku> findByVideoIdAndTimeFrom(@Param("videoId") Long videoId, @Param("startTime") Double startTime);

    List<Danmaku> findByUserIdAndIsDeletedOrderByCreatedAtDesc(Long userId, Boolean isDeleted);

    @Modifying
    @Query("UPDATE Danmaku d SET d.isDeleted = true WHERE d.id = :id AND d.userId = :userId")
    int softDeleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(d) FROM Danmaku d WHERE d.videoId = :videoId AND d.isDeleted = false")
    long countByVideoId(@Param("videoId") Long videoId);

    @Query("SELECT COUNT(d) FROM Danmaku d WHERE d.videoId = :videoId AND d.isDeleted = false AND d.createdAt >= :since")
    long countByVideoIdSince(@Param("videoId") Long videoId, @Param("since") LocalDateTime since);
}