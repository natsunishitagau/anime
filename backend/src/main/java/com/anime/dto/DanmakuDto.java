package com.anime.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DanmakuDto {
    private Long id;
    private Long videoId;
    private Long userId;
    private String username;
    private String content;
    private Double time;
    private String color;
    private Integer fontSize;
    private LocalDateTime createdAt;
    private Boolean isOwn;
}