package com.anime.service;

import com.anime.entity.Anime;
import com.anime.repository.AnimeRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final AnimeRepository animeRepository;

    public RecommendationService(AnimeRepository animeRepository) {
        this.animeRepository = animeRepository;
    }

    public List<Anime> getPopularAnime(int limit) {
        return animeRepository.findAll().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Anime> getSeasonalAnime(String season, Integer year, int limit) {
        return animeRepository.findAll().stream()
                .filter(a -> (season == null || season.equals(a.getSeason())) &&
                            (year == null || year.equals(a.getYear())))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Anime> getAnimeByGenre(String genre, int limit) {
        return animeRepository.findAll().stream()
                .filter(a -> a.getGenres() != null && a.getGenres().contains(genre))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Anime> getRecommendations(Long userId, int limit) {
        return getPopularAnime(limit);
    }

    public List<Anime> getCollaborativeRecommendations(Long userId, int limit) {
        return getPopularAnime(limit);
    }

    public List<Anime> getSimilarAnime(Long animeId, int limit) {
        Optional<Anime> targetAnime = animeRepository.findById(animeId);
        if (targetAnime.isEmpty()) {
            return getPopularAnime(limit);
        }

        Anime anime = targetAnime.get();
        String targetGenres = anime.getGenres();

        return animeRepository.findAll().stream()
                .filter(a -> !a.getId().equals(animeId) && a.getGenres() != null)
                .filter(a -> {
                    String[] targetGenreArray = targetGenres.split(",");
                    String[] animeGenreArray = a.getGenres().split(",");
                    for (String targetGenre : targetGenreArray) {
                        for (String animeGenre : animeGenreArray) {
                            if (targetGenre.trim().equals(animeGenre.trim())) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}