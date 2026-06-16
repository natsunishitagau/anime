package com.anime.controller;

import com.anime.dto.AnimeDto;
import com.anime.dto.DtoMapper;
import com.anime.dto.response.ApiResponse;
import com.anime.entity.Anime;
import com.anime.repository.AnimeRepository;
import com.anime.repository.AnimeSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/anime")
public class AnimeGameController {

    private final AnimeRepository animeRepository;
    private final DtoMapper dtoMapper;

    public AnimeGameController(AnimeRepository animeRepository, DtoMapper dtoMapper) {
        this.animeRepository = animeRepository;
        this.dtoMapper = dtoMapper;
    }

    @PostMapping("/game-sequence")
    public ResponseEntity<ApiResponse<List<AnimeDto>>> getGameSequence(@RequestBody GameSequenceRequest request) {
        Integer startYear = request.getStartYear();
        Integer endYear = request.getEndYear();
        String typeFilter = request.getTypeFilter();
        int count = request.getCount();

        var spec = SpecificationHelper.allOf(
                AnimeSpecifications.scoreGreaterThanZero(),
                AnimeSpecifications.hasImageUrl(),
                AnimeSpecifications.hasTitleJp(),
                AnimeSpecifications.hasYearBetween(startYear, endYear)
        );
        if (!"all".equals(typeFilter)) {
            spec = spec.and(AnimeSpecifications.hasType(typeFilter));
        }

        List<Anime> filtered = animeRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "score"));

        List<Anime> deduplicated = deduplicateByTitleJpPrefix(filtered);
        Map<Double, Anime> uniqueScoreMap = deduplicated.stream()
                .filter(a -> a.getScore() != null)
                .collect(Collectors.toMap(Anime::getScore, a -> a, (existing, replacement) -> existing));

        List<Anime> uniqueScoreList = new ArrayList<>(uniqueScoreMap.values());
        if (uniqueScoreList.size() < count) {
            return ResponseEntity.ok(ApiResponse.success(new ArrayList<>()));
        }
        Collections.shuffle(uniqueScoreList);
        List<AnimeDto> result = uniqueScoreList.stream().limit(count).map(dtoMapper::toAnimeDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private List<Anime> deduplicateByTitleJpPrefix(List<Anime> animes) {
        List<Anime> sorted = animes.stream()
                .filter(a -> a.getTitleJp() != null && !a.getTitleJp().isEmpty())
                .sorted(Comparator.comparingInt(a -> a.getTitleJp().length()))
                .collect(Collectors.toList());
        Set<String> seenPrefixes = new HashSet<>();
        List<Anime> result = new ArrayList<>();
        for (Anime anime : sorted) {
            String titleJp = anime.getTitleJp();
            boolean isSubset = false;
            for (String seen : seenPrefixes) {
                if (titleJp.startsWith(seen) || seen.startsWith(titleJp)) {
                    isSubset = true;
                    break;
                }
            }
            if (!isSubset) {
                result.add(anime);
                seenPrefixes.add(titleJp);
            }
        }
        return result;
    }

    public static class GameSequenceRequest {
        private Integer startYear;
        private Integer endYear;
        private String typeFilter;
        private int count;

        public Integer getStartYear() { return startYear; }
        public void setStartYear(Integer startYear) { this.startYear = startYear; }
        public Integer getEndYear() { return endYear; }
        public void setEndYear(Integer endYear) { this.endYear = endYear; }
        public String getTypeFilter() { return typeFilter; }
        public void setTypeFilter(String typeFilter) { this.typeFilter = typeFilter; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}

class SpecificationHelper {
    @SafeVarargs
    static org.springframework.data.jpa.domain.Specification<Anime> allOf(
            org.springframework.data.jpa.domain.Specification<Anime>... specs) {
        org.springframework.data.jpa.domain.Specification<Anime> result = org.springframework.data.jpa.domain.Specification.where(null);
        for (var spec : specs) {
            if (spec != null) result = result.and(spec);
        }
        return result;
    }
}
