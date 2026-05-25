package com.anime.repository;

import com.anime.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Favorite> findByUserIdAndFolderIdOrderByCreatedAtDesc(Long userId, Long folderId);
    Optional<Favorite> findByUserIdAndAnimeIdAndFolderId(Long userId, Long animeId, Long folderId);
    Optional<Favorite> findByUserIdAndAnimeId(Long userId, Long animeId);
    boolean existsByUserIdAndAnimeIdAndFolderId(Long userId, Long animeId, Long folderId);
    boolean existsByUserIdAndAnimeId(Long userId, Long animeId);
    void deleteByUserIdAndAnimeIdAndFolderId(Long userId, Long animeId, Long folderId);
    List<Favorite> findByAnimeId(Long animeId);
}