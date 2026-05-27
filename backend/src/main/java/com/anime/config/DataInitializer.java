package com.anime.config;

import com.anime.entity.AnimeVideo;
import com.anime.entity.DailyStats;
import com.anime.repository.AnimeVideoRepository;
import com.anime.repository.DailyStatsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.Random;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDailyStats(DailyStatsRepository dailyStatsRepository) {
        return args -> {
            if (dailyStatsRepository.count() == 0) {
                Random random = new Random();
                LocalDate today = LocalDate.now();
                
                for (int i = 0; i < 7; i++) {
                    LocalDate date = today.minusDays(i);
                    long pv = 5000 + random.nextLong(15000);
                    long uv = 800 + random.nextLong(2000);
                    
                    DailyStats stats = new DailyStats(date, pv, uv);
                    dailyStatsRepository.save(stats);
                    
                    System.out.println("Created daily stats for " + date + ": PV=" + pv + ", UV=" + uv);
                }
            }
        };
    }

    @Bean
    public CommandLineRunner initAnimeVideos(AnimeVideoRepository videoRepository) {
        return args -> {
            if (videoRepository.count() == 0) {
                Long testAnimeId = 52991L;
                String[] episodeTitles = {
                    "第一集：序幕",
                    "第二集：启程",
                    "第三集：邂逅",
                    "第四集：试炼",
                    "第五集：觉醒"
                };
                
                for (int i = 0; i < episodeTitles.length; i++) {
                    AnimeVideo video = new AnimeVideo();
                    video.setAnimeId(testAnimeId);
                    video.setTitle(episodeTitles[i]);
                    video.setEpisodeNumber(i + 1);
                    video.setDuration(1440 + (int)(Math.random() * 600));
                    video.setVideoPath("uploads/videos/test_video_" + (i + 1) + ".mp4");
                    video.setThumbnailPath("uploads/videos/thumbnail_" + (i + 1) + ".jpg");
                    videoRepository.save(video);
                    
                    System.out.println("Created video for anime " + testAnimeId + ": Episode " + (i + 1));
                }
            }
        };
    }
}
