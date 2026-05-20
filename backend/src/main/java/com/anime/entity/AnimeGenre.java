package com.anime.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "anime_genre")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(AnimeGenreId.class)
public class AnimeGenre {

    @Id
    @Column(name = "anime_id")
    private Long animeId;

    @Id
    @Column(name = "genre_id")
    private Long genreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anime_id", insertable = false, updatable = false)
    private Anime anime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", insertable = false, updatable = false)
    private Genre genre;
}
