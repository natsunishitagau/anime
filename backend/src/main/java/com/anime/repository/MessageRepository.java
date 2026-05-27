package com.anime.repository;

import com.anime.entity.Message;
import com.anime.entity.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Message> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead);
    List<Message> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, MessageType type);
    int countByUserIdAndIsRead(Long userId, Boolean isRead);
    boolean existsByUserIdAndTypeAndRelatedIdAndRelatedUserId(Long userId, MessageType type, Long relatedId, Long relatedUserId);
    Optional<Message> findByUserIdAndRelatedIdAndRelatedUserId(Long userId, Long relatedId, Long relatedUserId);
    void deleteByUserIdAndTypeAndRelatedIdAndRelatedUserId(Long userId, MessageType type, Long relatedId, Long relatedUserId);
}