package com.anime.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterGameSequenceRequest {
    private Integer minFavorites;
    private Integer maxFavorites;
    private Integer count;
}