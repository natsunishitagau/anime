package com.anime.repository;

import com.anime.entity.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, Long> {
    Optional<DailyStats> findByStatDate(LocalDate statDate);
}
