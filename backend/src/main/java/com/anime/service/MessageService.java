package com.anime.service;

import com.anime.config.RabbitMQConfig;
import com.anime.dto.MessageDto;
import com.anime.dto.MessageEvent;
import com.anime.entity.Message;
import com.anime.entity.MessageType;
import com.anime.repository.MessageRepository;
import com.anime.repository.UserRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MessageService {
    
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    
    public MessageService(MessageRepository messageRepository, 
                          UserRepository userRepository,
                          RabbitTemplate rabbitTemplate) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void sendMessage(MessageEvent event) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.MESSAGE_EXCHANGE,
            RabbitMQConfig.MESSAGE_ROUTING_KEY,
            event
        );
    }
    
    @RabbitListener(queues = RabbitMQConfig.MESSAGE_QUEUE)
    @Transactional
    public void handleMessage(MessageEvent event) {
        if (event.getType() == MessageType.REVIEW_LIKE) {
            Optional<Message> existingMessage = messageRepository.findByUserIdAndRelatedIdAndRelatedUserId(
                event.getUserId(),
                event.getRelatedId(),
                event.getRelatedUserId()
            );
            
            if (existingMessage.isPresent()) {
                Message msg = existingMessage.get();
                if (msg.getType() == MessageType.REVIEW_LIKE) {
                    return;
                }
                if (msg.getType() == MessageType.REVIEW_LIKE_CANCELLED) {
                    msg.setType(MessageType.REVIEW_LIKE);
                    msg.setTitle(event.getTitle());
                    msg.setContent(event.getContent());
                    msg.setIsRead(false);
                    messageRepository.save(msg);
                    return;
                }
            }
        }
        
        Message message = new Message();
        message.setUserId(event.getUserId());
        message.setType(event.getType());
        message.setTitle(event.getTitle());
        message.setContent(event.getContent());
        message.setRelatedId(event.getRelatedId());
        message.setRelatedUserId(event.getRelatedUserId());
        message.setIsRead(false);
        
        messageRepository.save(message);
    }
    
    public List<MessageDto> getUserMessages(Long userId) {
        List<Message> messages = messageRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return messages.stream().map(this::toDto).collect(Collectors.toList());
    }
    
    public List<MessageDto> getUnreadMessages(Long userId) {
        List<Message> messages = messageRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false);
        return messages.stream().map(this::toDto).collect(Collectors.toList());
    }
    
    public int getUnreadCount(Long userId) {
        return messageRepository.countByUserIdAndIsRead(userId, false);
    }
    
    @Transactional
    public void markAsRead(Long userId, Long messageId) {
        messageRepository.findById(messageId).ifPresent(message -> {
            if (message.getUserId().equals(userId)) {
                message.setIsRead(true);
                messageRepository.save(message);
            }
        });
    }
    
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Message> unreadMessages = messageRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false);
        unreadMessages.forEach(message -> {
            message.setIsRead(true);
            messageRepository.save(message);
        });
    }
    
    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        messageRepository.findById(messageId).ifPresent(message -> {
            if (message.getUserId().equals(userId)) {
                messageRepository.delete(message);
            }
        });
    }
    
    @Transactional
    public void sendAnnouncement(Long userId, String title, String content) {
        MessageEvent event = new MessageEvent();
        event.setUserId(userId);
        event.setType(MessageType.ADMIN_ANNOUNCEMENT);
        event.setTitle(title);
        event.setContent(content);
        sendMessage(event);
    }
    
    @Transactional
    public void sendReviewReplyNotification(Long reviewOwnerId, Long reviewerId, Long reviewId, String reviewerName, String replyContent) {
        if (reviewOwnerId.equals(reviewerId)) {
            return;
        }
        MessageEvent event = new MessageEvent();
        event.setUserId(reviewOwnerId);
        event.setType(MessageType.REVIEW_REPLY);
        event.setTitle("收到新回复");
        String displayContent = replyContent != null && replyContent.length() > 50 
            ? replyContent.substring(0, 50) + "..." 
            : replyContent;
        event.setContent(reviewerName + " 回复了你：" + displayContent);
        event.setRelatedId(reviewId);
        event.setRelatedUserId(reviewerId);
        sendMessage(event);
    }
    
    @Transactional
    public void sendReviewLikeNotification(Long reviewOwnerId, Long likerId, Long reviewId, String likerName, String ownerComment) {
        if (reviewOwnerId.equals(likerId)) {
            return;
        }
        MessageEvent event = new MessageEvent();
        event.setUserId(reviewOwnerId);
        event.setType(MessageType.REVIEW_LIKE);
        event.setTitle("收到点赞");
        String displayContent = ownerComment != null && ownerComment.length() > 50 
            ? ownerComment.substring(0, 50) + "..." 
            : ownerComment;
        event.setContent(likerName + " 点赞了你的评论：" + displayContent);
        event.setRelatedId(reviewId);
        event.setRelatedUserId(likerId);
        sendMessage(event);
    }
    
    @Transactional
    public void cancelReviewLikeNotification(Long reviewOwnerId, Long likerId, Long reviewId) {
        Optional<Message> existingMessage = messageRepository.findByUserIdAndRelatedIdAndRelatedUserId(
            reviewOwnerId, reviewId, likerId
        );
        if (existingMessage.isPresent()) {
            Message msg = existingMessage.get();
            if (msg.getType() == MessageType.REVIEW_LIKE) {
                msg.setType(MessageType.REVIEW_LIKE_CANCELLED);
                msg.setTitle("点赞已取消");
                msg.setIsRead(false);
                messageRepository.save(msg);
            }
        }
    }
    
    private MessageDto toDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setUserId(message.getUserId());
        dto.setType(message.getType());
        dto.setTitle(message.getTitle());
        dto.setContent(message.getContent());
        dto.setIsRead(message.getIsRead());
        dto.setRelatedId(message.getRelatedId());
        dto.setRelatedUserId(message.getRelatedUserId());
        dto.setCreatedAt(message.getCreatedAt());
        
        if (message.getRelatedUserId() != null) {
            userRepository.findById(message.getRelatedUserId()).ifPresent(user -> {
                dto.setRelatedUsername(user.getUsername());
            });
        }
        
        return dto;
    }
}