package com.anime.controller;

import com.anime.dto.ApiResponse;
import com.anime.entity.AnimeVideo;
import com.anime.service.AnimeVideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
@CrossOrigin
public class AnimeVideoController {

    @Autowired
    private AnimeVideoService videoService;

    @GetMapping("/anime/{animeId}")
    public ResponseEntity<ApiResponse<List<AnimeVideo>>> getVideosByAnime(@PathVariable Long animeId) {
        List<AnimeVideo> videos = videoService.getVideosByAnimeId(animeId);
        return ResponseEntity.ok(ApiResponse.success(videos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AnimeVideo>> getVideoById(@PathVariable Long id) {
        return videoService.getVideoById(id)
                .map(video -> ResponseEntity.ok(ApiResponse.success(video)))
                .orElse(ResponseEntity.status(404).body(ApiResponse.error("视频不存在")));
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<Resource> streamVideo(@PathVariable Long id) {
        var optionalVideo = videoService.getVideoById(id);
        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AnimeVideo video = optionalVideo.get();
        try {
            Path filePath = Paths.get(video.getVideoPath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("video/mp4"))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/thumbnail/{id}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long id) {
        var optionalVideo = videoService.getVideoById(id);
        if (optionalVideo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AnimeVideo video = optionalVideo.get();
        try {
            Path filePath = Paths.get(video.getThumbnailPath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}