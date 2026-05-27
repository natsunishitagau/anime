package com.anime.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDto {
    private Long id;
    private Long userId;
    private String username;
    private String avatarUrl;
    private Long animeId;
    private String comment;
    private String createdAt;
    private Integer likes;
    private Boolean liked;
    private Long topLevelId;
    private Long parentId;
    private Boolean isDeleted;
}