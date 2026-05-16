package com.anime.repository;

import com.anime.entity.Anime;
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
    
    @Query("SELECT a FROM Anime a WHERE a.genres LIKE %:genre%")
    List<Anime> findByGenre(@Param("genre") String genre);
    
    @Query("SELECT a FROM Anime a WHERE a.type = :type ORDER BY a.score DESC")
    List<Anime> findTopByType(@Param("type") String type, Pageable pageable);
    
    @Query("SELECT a FROM Anime a WHERE a.season = :season AND a.year = :year")
    List<Anime> findBySeasonAndYear(@Param("season") String season, @Param("year") Integer year);
    
    @Query("SELECT a FROM Anime a WHERE a.title LIKE %:query% OR a.titleJp LIKE %:query% OR a.synopsis LIKE %:query%")
    List<Anime> searchAnime(@Param("query") String query);
    
    @Query("SELECT a FROM Anime a ORDER BY a.score DESC")
    List<Anime> findTopRated(Pageable pageable);
    
    @Query("SELECT DISTINCT a.year FROM Anime a WHERE a.year IS NOT NULL ORDER BY a.year DESC")
    List<Integer> findDistinctYears();
}