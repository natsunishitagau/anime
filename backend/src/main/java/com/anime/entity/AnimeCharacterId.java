package com.anime.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimeCharacterId implements Serializable {

    private Long animeId;
    private Long characterId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnimeCharacterId that = (AnimeCharacterId) o;
        return Objects.equals(animeId, that.animeId) && Objects.equals(characterId, that.characterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(animeId, characterId);
    }
}
