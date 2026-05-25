package com.anime.repository;

import com.anime.entity.Anime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnimeRepository extends JpaRepository<Anime, Long> {

    List<Anime> findByType(String type);

    List<Anime> findBySeason(String season);

    List<Anime> findByYear(Integer year);

    List<Anime> findByStatus(String status);

    @Query("SELECT a FROM Anime a JOIN a.genres g WHERE g.name LIKE %:genre%")
    List<Anime> findByGenre(@Param("genre") String genre);

    @Query("SELECT a FROM Anime a WHERE a.type = :type ORDER BY a.score DESC")
    List<Anime> findTopByType(@Param("type") String type, Pageable pageable);

    @Query("SELECT a FROM Anime a WHERE a.season = :season AND a.year = :year")
    List<Anime> findBySeasonAndYear(@Param("season") String season, @Param("year") Integer year);

    @Query("SELECT a FROM Anime a WHERE a.title LIKE %:query% OR a.titleJp LIKE %:query% ORDER BY a.score DESC")
    List<Anime> searchAnime(@Param("query") String query);

    @Query("SELECT a FROM Anime a WHERE a.title LIKE %:keyword% OR a.titleJp LIKE %:keyword% ORDER BY a.score DESC")
    Page<Anime> searchAnimePage(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT a FROM Anime a ORDER BY a.score DESC")
    List<Anime> findTopRated(Pageable pageable);

    @Query("SELECT DISTINCT a.year FROM Anime a WHERE a.year IS NOT NULL ORDER BY a.year DESC")
    List<Integer> findDistinctYears();

    @Query("SELECT DISTINCT a.source FROM Anime a WHERE a.source IS NOT NULL AND a.source != '' ORDER BY a.source")
    List<String> findDistinctSources();

    @Query("SELECT a FROM Anime a LEFT JOIN FETCH a.genres WHERE a.id = :id")
    java.util.Optional<Anime> findByIdWithGenres(@Param("id") Long id);

    @Query("SELECT a.id FROM Anime a")
    List<Long> findAllAnimeIds();
}
