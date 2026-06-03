package com.anime.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSettingsResponse {
    private Boolean danmakuEnabled;
    private String danmakuColor;
    private Double defaultVolume;
}