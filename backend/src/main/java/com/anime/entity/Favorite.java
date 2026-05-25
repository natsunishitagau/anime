package com.anime.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "favorites", uniqueConstraints = {
    @UniqueConstraint(name = "UK_user_anime_folder", columnNames = {"user_id", "anime_id", "folder_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "anime_id", nullable = false)
    private Long animeId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anime_id", insertable = false, updatable = false)
    private Anime anime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", insertable = false, updatable = false)
    private FavoriteFolder folder;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}