package com.anime.dto;

import com.anime.entity.MessageType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long userId;
    private MessageType type;
    private String title;
    private String content;
    private Long relatedId;
    private Long relatedUserId;
}