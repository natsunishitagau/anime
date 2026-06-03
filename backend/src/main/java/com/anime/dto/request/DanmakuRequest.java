package com.anime.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DanmakuRequest {
    private Long videoId;
    private String content;
    private Double time;
    private String color;
    private Integer fontSize;
}