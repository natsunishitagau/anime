package com.anime.service;

import com.anime.dto.DanmakuDto;
import com.anime.dto.request.DanmakuRequest;
import com.anime.entity.Danmaku;
import com.anime.repository.DanmakuRepository;
import com.anime.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DanmakuService {

    private final DanmakuRepository danmakuRepository;
    private final UserRepository userRepository;

    public DanmakuService(DanmakuRepository danmakuRepository, UserRepository userRepository) {
        this.danmakuRepository = danmakuRepository;
        this.userRepository = userRepository;
    }

    public List<DanmakuDto> getDanmakuByVideoId(Long videoId, Long currentUserId) {
        List<Danmaku> danmakuList = danmakuRepository.findByVideoIdAndIsDeletedOrderByTimeAsc(videoId, false);
        return danmakuList.stream().map(d -> toDto(d, currentUserId)).collect(Collectors.toList());
    }

    public List<DanmakuDto> getDanmakuByVideoIdAndTimeRange(Long videoId, Double startTime, Double endTime, Long currentUserId) {
        List<Danmaku> danmakuList = danmakuRepository.findByVideoIdAndTimeRange(videoId, startTime, endTime);
        return danmakuList.stream().map(d -> toDto(d, currentUserId)).collect(Collectors.toList());
    }
    
    public List<DanmakuDto> getDanmakuByVideoIdAndMinute(Long videoId, int minute, Long currentUserId) {
        double startTime = minute * 60.0;
        double endTime = (minute + 1) * 60.0;
        return getDanmakuByVideoIdAndTimeRange(videoId, startTime, endTime, currentUserId);
    }

    @Transactional
    public DanmakuDto sendDanmaku(Long userId, DanmakuRequest request) {
        Danmaku danmaku = new Danmaku();
        danmaku.setVideoId(request.getVideoId());
        danmaku.setUserId(userId);
        danmaku.setContent(request.getContent());
        danmaku.setTime(request.getTime());
        danmaku.setColor(request.getColor() != null ? request.getColor() : "#FFFFFF");
        danmaku.setFontSize(request.getFontSize() != null ? request.getFontSize() : 25);
        
        Danmaku savedDanmaku = danmakuRepository.save(danmaku);
        return toDto(savedDanmaku, userId);
    }

    @Transactional
    public Long deleteDanmaku(Long userId, Long danmakuId) {
        Optional<Danmaku> danmaku = danmakuRepository.findById(danmakuId);
        if (danmaku.isPresent() && danmaku.get().getUserId().equals(userId)) {
            int deleted = danmakuRepository.softDeleteByIdAndUserId(danmakuId, userId);
            if (deleted > 0) {
                return danmaku.get().getVideoId();
            }
        }
        return null;
    }

    @Transactional
    public boolean hardDeleteDanmaku(Long userId, Long danmakuId) {
        Optional<Danmaku> danmaku = danmakuRepository.findById(danmakuId);
        if (danmaku.isPresent() && danmaku.get().getUserId().equals(userId)) {
            danmakuRepository.delete(danmaku.get());
            return true;
        }
        return false;
    }

    public long countDanmakuByVideoId(Long videoId) {
        return danmakuRepository.countByVideoId(videoId);
    }

    private DanmakuDto toDto(Danmaku danmaku, Long currentUserId) {
        DanmakuDto dto = new DanmakuDto();
        dto.setId(danmaku.getId());
        dto.setVideoId(danmaku.getVideoId());
        dto.setUserId(danmaku.getUserId());
        dto.setContent(danmaku.getContent());
        dto.setTime(danmaku.getTime());
        dto.setColor(danmaku.getColor());
        dto.setFontSize(danmaku.getFontSize());
        dto.setCreatedAt(danmaku.getCreatedAt());

        userRepository.findById(danmaku.getUserId()).ifPresent(user -> {
            dto.setUsername(user.getUsername());
        });

        return dto;
    }
}