package com.anime.repository;

import com.anime.entity.AnimeGenre;
import com.anime.entity.AnimeGenreId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnimeGenreRepository extends JpaRepository<AnimeGenre, AnimeGenreId> {
    List<AnimeGenre> findByAnimeId(Long animeId);
    void deleteByAnimeId(Long animeId);
    List<AnimeGenre> findByGenreId(Long genreId);
}
