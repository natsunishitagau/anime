package com.anime.service;

import com.anime.entity.Anime;
import com.anime.repository.AnimeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.*;
import java.util.Arrays;

@Service
public class AnimeSyncService {

    private final AnimeRepository animeRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> GENRE_TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> TYPE_TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> STATUS_TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> SOURCE_TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> SEASON_TRANSLATIONS = new HashMap<>();

    static {
        GENRE_TRANSLATIONS.put("Action", "动作");
        GENRE_TRANSLATIONS.put("Adventure", "冒险");
        GENRE_TRANSLATIONS.put("Avant Garde", "前卫艺术");
        GENRE_TRANSLATIONS.put("Award Winning", "获奖作品");
        GENRE_TRANSLATIONS.put("Boys Love", "耽美");
        GENRE_TRANSLATIONS.put("Comedy", "喜剧");
        GENRE_TRANSLATIONS.put("Drama", "剧情");
        GENRE_TRANSLATIONS.put("Fantasy", "奇幻");
        GENRE_TRANSLATIONS.put("Girls Love", "百合");
        GENRE_TRANSLATIONS.put("Gourmet", "美食");
        GENRE_TRANSLATIONS.put("Horror", "恐怖");
        GENRE_TRANSLATIONS.put("Mystery", "悬疑");
        GENRE_TRANSLATIONS.put("Romance", "爱情");
        GENRE_TRANSLATIONS.put("Sci-Fi", "科幻");
        GENRE_TRANSLATIONS.put("Slice of Life", "日常");
        GENRE_TRANSLATIONS.put("Sports", "运动");
        GENRE_TRANSLATIONS.put("Supernatural", "超自然");
        GENRE_TRANSLATIONS.put("Suspense", "惊悚");
        GENRE_TRANSLATIONS.put("Ecchi", "邪典");
        GENRE_TRANSLATIONS.put("Erotica", "情色");
        GENRE_TRANSLATIONS.put("Hentai", "色情");
        GENRE_TRANSLATIONS.put("Adult Cast", "成人角色");
        GENRE_TRANSLATIONS.put("Children", "儿童");
        GENRE_TRANSLATIONS.put("Gourmet", "美食");
        GENRE_TRANSLATIONS.put("Romantic Subtext", "浪漫隐含");
        GENRE_TRANSLATIONS.put("School", "校园");
        GENRE_TRANSLATIONS.put("Work Life", "职场");
        GENRE_TRANSLATIONS.put("Martial Arts", "武术");
        GENRE_TRANSLATIONS.put("Mecha", "机甲");
        GENRE_TRANSLATIONS.put("Music", "音乐");
        GENRE_TRANSLATIONS.put("Mythology", "神话");
        GENRE_TRANSLATIONS.put("Psychological", "心理");
        GENRE_TRANSLATIONS.put("Racing", "赛车");
        GENRE_TRANSLATIONS.put("Samurai", "武士");
        GENRE_TRANSLATIONS.put("Satire", "讽刺");
        GENRE_TRANSLATIONS.put("Shoujo", "少女");
        GENRE_TRANSLATIONS.put("Shounen", "少年");
        GENRE_TRANSLATIONS.put("Space", "太空");
        GENRE_TRANSLATIONS.put("Strategy Game", "策略游戏");
        GENRE_TRANSLATIONS.put("Super Power", "超能力");
        GENRE_TRANSLATIONS.put("Time Travel", "时空穿越");
        GENRE_TRANSLATIONS.put("Vampire", "吸血鬼");
        GENRE_TRANSLATIONS.put("Video Game", "电子游戏");
        GENRE_TRANSLATIONS.put("Visual Arts", "视觉艺术");
        GENRE_TRANSLATIONS.put("Isekai", "异世界");
        GENRE_TRANSLATIONS.put("Civil War", "内战");
        GENRE_TRANSLATIONS.put("Crossdressing", "女扮男装");

        TYPE_TRANSLATIONS.put("TV", "TV动画");
        TYPE_TRANSLATIONS.put("Movie", "剧场版");
        TYPE_TRANSLATIONS.put("OVA", "OVA");
        TYPE_TRANSLATIONS.put("ONA", "网盘动画");
        TYPE_TRANSLATIONS.put("Special", "特别篇");
        TYPE_TRANSLATIONS.put("Music", "音乐");
        TYPE_TRANSLATIONS.put("CM", "宣传片");
        TYPE_TRANSLATIONS.put("PV", "预告片");
        TYPE_TRANSLATIONS.put("TV Special", "TV特别篇");

        STATUS_TRANSLATIONS.put("Finished Airing", "已完结");
        STATUS_TRANSLATIONS.put("Currently Airing", "连载中");
        STATUS_TRANSLATIONS.put("Not yet aired", "未播出");
        STATUS_TRANSLATIONS.put("Hiatus", "休刊中");

        SOURCE_TRANSLATIONS.put("Manga", "漫画");
        SOURCE_TRANSLATIONS.put("Light novel", "轻小说");
        SOURCE_TRANSLATIONS.put("Visual novel", "视觉小说");
        SOURCE_TRANSLATIONS.put("Original", "原创");
        SOURCE_TRANSLATIONS.put("Web novel", "网络小说");
        SOURCE_TRANSLATIONS.put("Novel", "小说");
        SOURCE_TRANSLATIONS.put("4-koma manga", "四格漫画");
        SOURCE_TRANSLATIONS.put("Picture book", "绘本");
        SOURCE_TRANSLATIONS.put("Web manga", "网络漫画");
        SOURCE_TRANSLATIONS.put("Game", "游戏");
        SOURCE_TRANSLATIONS.put("Music", "音乐");
        SOURCE_TRANSLATIONS.put("Radio", "广播");
        SOURCE_TRANSLATIONS.put("Book", "书籍");
        SOURCE_TRANSLATIONS.put("Mixed media", "混合媒体");
        SOURCE_TRANSLATIONS.put("Other", "其他");
        SOURCE_TRANSLATIONS.put("Wikipedia", "维基百科");
        SOURCE_TRANSLATIONS.put("Anime official website", "动画官网");

        SEASON_TRANSLATIONS.put("spring", "春季");
        SEASON_TRANSLATIONS.put("summer", "夏季");
        SEASON_TRANSLATIONS.put("fall", "秋季");
        SEASON_TRANSLATIONS.put("winter", "冬季");
    }

    public AnimeSyncService(AnimeRepository animeRepository) {
        this.animeRepository = animeRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String syncTopAnime(int page, int limit) {
        String url = String.format("https://api.jikan.moe/v4/top/anime?page=%d&limit=%d", page, limit);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode dataArray = root.get("data");

            int importedCount = 0;
            for (JsonNode animeNode : dataArray) {
                Anime anime = parseAnime(animeNode);
                if (anime != null) {
                    animeRepository.save(anime);
                    importedCount++;
                }
            }

            return String.format("成功导入 %d 条动漫数据 (第 %d 页)", importedCount, page);
        } catch (Exception e) {
            return "导入失败: " + e.getMessage();
        }
    }

    public String syncAllAnime(int maxPages) {
        int totalImported = 0;
        for (int page = 1; page <= maxPages; page++) {
            String url = String.format("https://api.jikan.moe/v4/top/anime?page=%d&limit=25", page);

            try {
                Thread.sleep(1500);

                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode dataArray = root.get("data");

                if (dataArray == null || dataArray.isEmpty()) {
                    break;
                }

                int pageCount = 0;
                for (JsonNode animeNode : dataArray) {
                    Anime anime = parseAnime(animeNode);
                    if (anime != null) {
                        animeRepository.save(anime);
                        pageCount++;
                    }
                }
                totalImported += pageCount;

            } catch (Exception e) {
                return "导入失败 (第 " + page + " 页): " + e.getMessage();
            }
        }

        return String.format("导入完成，共 %d 条数据", totalImported);
    }

    private Anime parseAnime(JsonNode node) {
        try {
            Anime anime = new Anime();

            JsonNode malIdNode = node.get("mal_id");
            if (malIdNode != null && !malIdNode.isNull()) {
                anime.setId(malIdNode.asLong());
            }

            anime.setTitle(getTextValue(node, "title"));

            JsonNode titleJapanese = node.get("title_japanese");
            if (titleJapanese != null && !titleJapanese.isNull()) {
                anime.setTitleJp(titleJapanese.asText());
            } else {
                anime.setTitleJp("");
            }

            anime.setSynopsis(getTextValue(node, "synopsis"));

            JsonNode images = node.get("images");
            if (images != null) {
                JsonNode jpg = images.get("jpg");
                if (jpg != null) {
                    anime.setImageUrl(getTextValue(jpg, "image_url"));
                }
            }

            JsonNode score = node.get("score");
            if (score != null && !score.isNull()) {
                anime.setScore(score.asDouble());
            } else {
                anime.setScore(0.0);
            }

            JsonNode episodes = node.get("episodes");
            if (episodes != null && !episodes.isNull()) {
                anime.setEpisodes(episodes.asInt());
            } else {
                anime.setEpisodes(0);
            }

            anime.setType(translateType(getTextValue(node, "type")));

            anime.setSeason(translateSeason(getTextValue(node, "season")));

            JsonNode year = node.get("year");
            if (year != null && !year.isNull()) {
                anime.setYear(year.asInt());
            }

            anime.setStatus(translateStatus(getTextValue(node, "status")));

            anime.setStudios(extractStudios(node));

            anime.setGenres(translateGenres(node));

            anime.setSource(translateSource(getTextValue(node, "source")));

            return anime;
        } catch (Exception e) {
            return null;
        }
    }

    private String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null && !fieldNode.isNull()) {
            return fieldNode.asText();
        }
        return "";
    }

    private String extractStudios(JsonNode node) {
        JsonNode studios = node.get("studios");
        if (studios != null && studios.isArray()) {
            List<String> studioList = new ArrayList<>();
            for (JsonNode studio : studios) {
                String name = studio.get("name").asText();
                if (name != null) {
                    studioList.add(name);
                }
            }
            return String.join(", ", studioList);
        }
        return "";
    }

    private String translateGenres(JsonNode node) {
        JsonNode genres = node.get("genres");
        List<String> translatedGenres = new ArrayList<>();

        if (genres != null && genres.isArray()) {
            for (JsonNode genre : genres) {
                String name = genre.get("name").asText();
                String translated = GENRE_TRANSLATIONS.getOrDefault(name, name);
                translatedGenres.add(translated);
            }
        }

        JsonNode themes = node.get("themes");
        if (themes != null && themes.isArray()) {
            for (JsonNode theme : themes) {
                String name = theme.get("name").asText();
                String translated = GENRE_TRANSLATIONS.getOrDefault(name, name);
                translatedGenres.add(translated);
            }
        }

        JsonNode demographics = node.get("demographics");
        if (demographics != null && demographics.isArray()) {
            for (JsonNode demo : demographics) {
                String name = demo.get("name").asText();
                String translated = GENRE_TRANSLATIONS.getOrDefault(name, name);
                translatedGenres.add(translated);
            }
        }

        return String.join(", ", translatedGenres);
    }

    private String translateType(String type) {
        return TYPE_TRANSLATIONS.getOrDefault(type, type);
    }

    private String translateStatus(String status) {
        return STATUS_TRANSLATIONS.getOrDefault(status, status);
    }

    private String translateSource(String source) {
        return SOURCE_TRANSLATIONS.getOrDefault(source, source);
    }

    private String translateSeason(String season) {
        return SEASON_TRANSLATIONS.getOrDefault(season, season);
    }

    public String cleanAnimeData() {
        List<Anime> allAnime = animeRepository.findAll();
        int deletedCount = 0;
        int cleanedCount = 0;

        List<String> forbiddenTags = Arrays.asList("百合", "色情", 
        "女扮男装", "邪典", "恐怖", "成人角色", "获奖作品");

        for (Anime anime : allAnime) {
            String genres = anime.getGenres();
            boolean shouldDelete = false;

            if (genres != null && !genres.isEmpty()) {
                for (String tag : forbiddenTags) {
                    if (genres.contains(tag)) {
                        shouldDelete = true;
                        break;
                    }
                }
            }

            if (shouldDelete) {
                animeRepository.delete(anime);
                deletedCount++;
            } else {
                String cleanedGenres = cleanGenres(genres);
                if (!cleanedGenres.equals(genres)) {
                    anime.setGenres(cleanedGenres);
                    animeRepository.save(anime);
                    cleanedCount++;
                }
            }
        }

        return String.format("清理完成：删除 %d 条违规数据，清理 %d 条数据的英文标签", deletedCount, cleanedCount);
    }

    private String cleanGenres(String genres) {
        if (genres == null || genres.isEmpty()) {
            return "";
        }

        List<String> genreList = Arrays.asList(genres.split(", "));
        List<String> cleanedList = new ArrayList<>();

        for (String genre : genreList) {
            if (GENRE_TRANSLATIONS.containsValue(genre)) {
                cleanedList.add(genre);
            }
        }

        return String.join(", ", cleanedList);
    }
}