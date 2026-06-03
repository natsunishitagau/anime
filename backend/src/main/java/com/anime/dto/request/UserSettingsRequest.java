package com.anime.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserSettingsRequest {
    private Boolean danmakuEnabled;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "弹幕颜色格式必须是十六进制颜色值，如 #FFFFFF")
    private String danmakuColor;

    @DecimalMin(value = "0.0", message = "音量最小值为0")
    @DecimalMax(value = "1.0", message = "音量最大值为1")
    private Double defaultVolume;
}