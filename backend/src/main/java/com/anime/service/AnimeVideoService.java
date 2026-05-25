package com.anime.service;

import com.anime.entity.AnimeVideo;
import com.anime.repository.AnimeVideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AnimeVideoService {

    @Autowired
    private AnimeVideoRepository videoRepository;

    public List<AnimeVideo> getVideosByAnimeId(Long animeId) {
        return videoRepository.findByAnimeIdOrderByEpisodeNumberAsc(animeId);
    }

    public Optional<AnimeVideo> getVideoById(Long id) {
        return videoRepository.findById(id);
    }

    public AnimeVideo saveVideo(AnimeVideo video) {
        return videoRepository.save(video);
    }

    public void deleteVideo(Long id) {
        videoRepository.deleteById(id);
    }
}