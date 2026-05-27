package com.anime.dto;

import com.anime.entity.MessageType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private Long id;
    private Long userId;
    private MessageType type;
    private String title;
    private String content;
    private Boolean isRead;
    private Long relatedId;
    private Long relatedUserId;
    private String relatedUsername;
    private LocalDateTime createdAt;
}