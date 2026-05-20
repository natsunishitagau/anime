package com.anime.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "anime_character")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(AnimeCharacterId.class)
public class AnimeCharacter {

    @Id
    @Column(name = "anime_id")
    private Long animeId;

    @Id
    @Column(name = "character_id")
    private Long characterId;

    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anime_id", insertable = false, updatable = false)
    private Anime anime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", insertable = false, updatable = false)
    private Character character;
}
