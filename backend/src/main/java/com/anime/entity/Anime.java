package com.anime.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "anime")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Anime {
    @Id
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "title_jp")
    private String titleJp;

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "image_url")
    private String imageUrl;

    private Double score = 0.0;

    private Integer episodes = 0;

    private String type;

    private String season;

    private Integer year;

    private String status;

    private String studios;

    private String genres;

    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}