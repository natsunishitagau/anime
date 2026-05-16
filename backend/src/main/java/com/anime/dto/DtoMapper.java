package com.anime.dto;

import com.anime.entity.Anime;
import com.anime.entity.Character;
import com.anime.entity.Review;

import java.util.List;
import java.util.stream.Collectors;

public class DtoMapper {

    public static AnimeDto toAnimeDto(Anime anime) {
        if (anime == null) return null;
        return new AnimeDto(
                anime.getId(),
                anime.getTitle(),
                anime.getTitleJp(),
                anime.getSynopsis(),
                anime.getImageUrl(),
                anime.getScore(),
                anime.getEpisodes(),
                anime.getType(),
                anime.getSeason(),
                anime.getYear(),
                anime.getStatus(),
                anime.getStudios(),
                anime.getGenres(),
                anime.getSource()
        );
    }

    public static List<AnimeDto> toAnimeDtoList(List<Anime> animeList) {
        if (animeList == null) return List.of();
        return animeList.stream()
                .map(DtoMapper::toAnimeDto)
                .collect(Collectors.toList());
    }

    public static CharacterDto toCharacterDto(Character character) {
        if (character == null) return null;
        return new CharacterDto(
                character.getId(),
                character.getName(),
                character.getNameJp(),
                character.getRole(),
                character.getImageUrl()
        );
    }

    public static List<CharacterDto> toCharacterDtoList(List<Character> characters) {
        if (characters == null) return List.of();
        return characters.stream()
                .map(DtoMapper::toCharacterDto)
                .collect(Collectors.toList());
    }

    public static ReviewDto toReviewDto(Review review) {
        if (review == null) return null;
        return new ReviewDto(
                review.getId(),
                review.getUserId(),
                review.getUsername(),
                review.getAnimeId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt() != null ? review.getCreatedAt().toString() : null
        );
    }

    public static List<ReviewDto> toReviewDtoList(List<Review> reviews) {
        if (reviews == null) return List.of();
        return reviews.stream()
                .map(DtoMapper::toReviewDto)
                .collect(Collectors.toList());
    }
}