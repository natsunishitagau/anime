package com.anime.repository;

import com.anime.entity.AnimeCharacter;
import com.anime.entity.AnimeCharacterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnimeCharacterRepository extends JpaRepository<AnimeCharacter, AnimeCharacterId> {
    List<AnimeCharacter> findByAnimeId(Long animeId);
    void deleteByAnimeId(Long animeId);
}
