package com.anime.service;

import com.anime.entity.WatchHistory;
import com.anime.repository.WatchHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WatchHistoryService {

    @Autowired
    private WatchHistoryRepository watchHistoryRepository;

    public WatchHistory saveOrUpdateProgress(Long userId, Long animeId, Long episodeId, Integer episodeNumber, Integer progress, Boolean completed) {
        Optional<WatchHistory> existing = watchHistoryRepository.findByUserIdAndAnimeId(userId, animeId);
        
        WatchHistory watchHistory;
        if (existing.isPresent()) {
            watchHistory = existing.get();
            
            if (episodeId != null && episodeId.equals(watchHistory.getEpisodeId())) {
                if (progress != null) {
                    Integer currentProgress = watchHistory.getProgress();
                    if (currentProgress == null || progress > currentProgress) {
                        watchHistory.setProgress(progress);
                    }
                }
                if (completed != null) {
                    watchHistory.setCompleted(completed);
                }
            } else {
                // 新集：覆盖记录
                watchHistory.setEpisodeId(episodeId);
                watchHistory.setEpisodeNumber(episodeNumber);
                watchHistory.setProgress(progress != null ? progress : 0);
                watchHistory.setCompleted(completed != null ? completed : false);
            }
        } else {
            watchHistory = new WatchHistory();
            watchHistory.setUserId(userId);
            watchHistory.setAnimeId(animeId);
            watchHistory.setEpisodeId(episodeId);
            watchHistory.setEpisodeNumber(episodeNumber);
            watchHistory.setProgress(progress != null ? progress : 0);
            watchHistory.setCompleted(completed != null ? completed : false);
        }
        
        return watchHistoryRepository.save(watchHistory);
    }

    public Optional<WatchHistory> getByUserIdAndAnimeId(Long userId, Long animeId) {
        return watchHistoryRepository.findByUserIdAndAnimeId(userId, animeId);
    }

    public List<WatchHistory> getByUserId(Long userId) {
        return watchHistoryRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public void deleteById(Long id) {
        watchHistoryRepository.deleteById(id);
    }
}