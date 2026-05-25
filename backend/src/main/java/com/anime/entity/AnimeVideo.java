package com.anime.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "anime_video")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimeVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anime_id", nullable = false)
    private Long animeId;

    @Column(nullable = false)
    private String title;

    @Column(name = "episode_number")
    private Integer episodeNumber;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "video_path", nullable = false)
    private String videoPath;

    @Column(name = "thumbnail_path")
    private String thumbnailPath;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}