package com.anime.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "review_likes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "review_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;
}