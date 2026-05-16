package com.anime.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimeDto {
    private Long id;
    private String title;
    private String titleJp;
    private String synopsis;
    private String imageUrl;
    private Double score;
    private Integer episodes;
    private String type;
    private String season;
    private Integer year;
    private String status;
    private String studios;
    private String genres;
    private String source;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class AnimeDetailDto {
    private AnimeDto anime;
    private java.util.List<CharacterDto> characters;
    private java.util.List<ReviewDto> reviews;
    private java.util.List<AnimeDto> similarAnime;
    private Boolean isFavorited;
    private Integer userRating;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class CharacterDto {
    private Long id;
    private String name;
    private String nameJp;
    private String role;
    private String imageUrl;
}