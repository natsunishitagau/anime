package com.anime.dto;

import com.anime.entity.Anime;
import com.anime.entity.Character;
import com.anime.entity.Genre;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DtoMapper {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public AnimeDto toAnimeDto(Anime anime) {
        if (anime == null) return null;
        
        // 直接查询 genres
        List<GenreDto> genreDtos = null;
        String sql = "SELECT g.id, g.name FROM genres g " +
                     "JOIN anime_genre ag ON g.id = ag.genre_id " +
                     "WHERE ag.anime_id = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, anime.getId());
        genreDtos = rows.stream()
                .map(row -> new GenreDto(
                        (Long) row.get("id"),
                        (String) row.get("name")
                ))
                .collect(Collectors.toList());
        
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
                genreDtos,
                anime.getSource()
        );
    }

    public List<AnimeDto> toAnimeDtoList(List<Anime> animeList) {
        if (animeList == null) return List.of();
        return animeList.stream()
                .map(this::toAnimeDto)
                .collect(Collectors.toList());
    }

    public GenreDto toGenreDto(Genre genre) {
        if (genre == null) return null;
        return new GenreDto(
                genre.getId(),
                genre.getName()
        );
    }

    public CharacterDto toCharacterDto(Character character) {
        if (character == null) return null;
        
        String animeTitleJp = null;
        String sql = "SELECT a.title_jp FROM anime a " +
                     "JOIN anime_character ac ON a.id = ac.anime_id " +
                     "WHERE ac.character_id = ? LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, character.getId());
        if (!rows.isEmpty()) {
            animeTitleJp = (String) rows.get(0).get("title_jp");
        }
        
        return new CharacterDto(
                character.getId(),
                character.getName(),
                character.getNameJp(),
                character.getImageUrl(),
                character.getFavorites(),
                animeTitleJp
        );
    }

    public List<CharacterDto> toCharacterDtoList(List<Character> characters) {
        if (characters == null) return List.of();
        return characters.stream()
                .map(this::toCharacterDto)
                .collect(Collectors.toList());
    }
}