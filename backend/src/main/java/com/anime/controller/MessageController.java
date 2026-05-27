package com.anime.controller;

import com.anime.dto.ApiResponse;
import com.anime.dto.MessageDto;
import com.anime.dto.UserPrincipal;
import com.anime.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/messages")
public class MessageController {
    
    private final MessageService messageService;
    
    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<MessageDto>>> getUserMessages(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        List<MessageDto> messages = messageService.getUserMessages(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(messages));
    }
    
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<MessageDto>>> getUnreadMessages(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        List<MessageDto> messages = messageService.getUnreadMessages(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(messages));
    }
    
    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getUnreadCount(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        int count = messageService.getUnreadCount(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }
    
    @PutMapping("/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long messageId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        messageService.markAsRead(userPrincipal.getId(), messageId);
        return ResponseEntity.ok(ApiResponse.success("Message marked as read", null));
    }
    
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        messageService.markAllAsRead(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success("All messages marked as read", null));
    }
    
    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable Long messageId,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        messageService.deleteMessage(userPrincipal.getId(), messageId);
        return ResponseEntity.ok(ApiResponse.success("Message deleted", null));
    }
}