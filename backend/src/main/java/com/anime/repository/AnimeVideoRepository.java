package com.anime.repository;

import com.anime.entity.AnimeVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnimeVideoRepository extends JpaRepository<AnimeVideo, Long> {
    List<AnimeVideo> findByAnimeIdOrderByEpisodeNumberAsc(Long animeId);
}