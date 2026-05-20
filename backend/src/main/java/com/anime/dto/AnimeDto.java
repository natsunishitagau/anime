package com.anime.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

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
    private List<GenreDto> genres;
    private String source;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class AnimeDetailDto {
    private AnimeDto anime;
    private List<CharacterDto> characters;
    private List<ReviewDto> reviews;
    private List<AnimeDto> similarAnime;
    private Boolean isFavorited;
    private Integer userRating;
}
