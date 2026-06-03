package com.anime.controller;

import com.anime.dto.CharacterDto;
import com.anime.dto.DtoMapper;
import com.anime.dto.request.CharacterGameSequenceRequest;
import com.anime.dto.response.ApiResponse;
import com.anime.entity.Character;
import com.anime.repository.CharacterRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/character")
public class CharacterController {

    private final CharacterRepository characterRepository;
    private final DtoMapper dtoMapper;

    public CharacterController(CharacterRepository characterRepository, DtoMapper dtoMapper) {
        this.characterRepository = characterRepository;
        this.dtoMapper = dtoMapper;
    }

    @PostMapping("/game-sequence")
    public ResponseEntity<ApiResponse<List<CharacterDto>>> getGameSequence(
            @RequestBody CharacterGameSequenceRequest request) {

        Integer minFavorites = request.getMinFavorites();
        Integer maxFavorites = request.getMaxFavorites();
        int count = request.getCount();

        List<Character> allCharacters = characterRepository.findAll();

        List<Character> filtered = allCharacters.stream()
                .filter(character -> {
                    if (character.getFavorites() == null || character.getFavorites() == 0) {
                        return false;
                    }
                    if (character.getImageUrl() == null || character.getImageUrl().isEmpty()) {
                        return false;
                    }
                    if (character.getName() == null || character.getName().isEmpty()) {
                        return false;
                    }
                    return true;
                })
                .filter(character -> {
                    if (minFavorites == null && maxFavorites == null) {
                        return true;
                    }
                    if (character.getFavorites() == null) {
                        return false;
                    }
                    boolean aboveMin = minFavorites == null || character.getFavorites() >= minFavorites;
                    boolean belowMax = maxFavorites == null || character.getFavorites() <= maxFavorites;
                    return aboveMin && belowMax;
                })
                .collect(Collectors.toList());

        if (filtered.size() < count) {
            return ResponseEntity.ok(ApiResponse.success(new ArrayList<>()));
        }

        Collections.shuffle(filtered);

        List<CharacterDto> result = filtered.stream()
                .limit(count)
                .map(dtoMapper::toCharacterDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}