package com.anime.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "daily_stats")
public class DailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", nullable = false, unique = true)
    private LocalDate statDate;

    @Column(name = "pv", nullable = false)
    private Long pv;

    @Column(name = "uv", nullable = false)
    private Long uv;

    @Column(name = "created_at")
    private LocalDate createdAt;

    public DailyStats() {}

    public DailyStats(LocalDate statDate, Long pv, Long uv) {
        this.statDate = statDate;
        this.pv = pv;
        this.uv = uv;
        this.createdAt = LocalDate.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public Long getPv() { return pv; }
    public void setPv(Long pv) { this.pv = pv; }
    public Long getUv() { return uv; }
    public void setUv(Long uv) { this.uv = uv; }
    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
}
