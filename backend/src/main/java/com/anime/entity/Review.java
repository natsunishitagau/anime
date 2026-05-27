package com.anime.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "anime_id", nullable = false)
    private Long animeId;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "username")
    private String username;

    @Column(name = "likes")
    private Integer likes = 0;

    @Column(name = "top_level_id")
    private Long topLevelId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (likes == null) {
            likes = 0;
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
        if (topLevelId == null) {
            topLevelId = id;
        }
    }
}