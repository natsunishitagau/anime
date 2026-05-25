package com.anime.repository;

import com.anime.entity.FavoriteFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteFolderRepository extends JpaRepository<FavoriteFolder, Long> {
    List<FavoriteFolder> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<FavoriteFolder> findByUserIdOrderBySortOrderAsc(Long userId);
    Optional<FavoriteFolder> findByUserIdAndName(Long userId, String name);
    boolean existsByUserIdAndName(Long userId, String name);
}
