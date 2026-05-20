package com.anime.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "anime")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"characters", "genres"})
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

    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(
            name = "anime_character",
            joinColumns = @JoinColumn(name = "anime_id"),
            inverseJoinColumns = @JoinColumn(name = "character_id")
    )
    private Set<Character> characters;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "anime_genre",
            joinColumns = @JoinColumn(name = "anime_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
