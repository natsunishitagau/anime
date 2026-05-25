package com.anime.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchHistoryDto {
    private Long id;
    private Long userId;
    private Long animeId;
    private Long episodeId;
    private Integer episodeNumber;
    private Integer progress;
    private Boolean completed;
    private LocalDateTime updatedAt;
}
