package com.anime.service;

import com.anime.entity.Anime;
import com.anime.entity.Character;
import com.anime.entity.AnimeCharacter;
import com.anime.entity.Genre;
import com.anime.entity.AnimeGenre;
import com.anime.repository.AnimeRepository;
import com.anime.repository.CharacterRepository;
import com.anime.repository.AnimeCharacterRepository;
import com.anime.repository.GenreRepository;
import com.anime.repository.AnimeGenreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.*;

@Service
public class AnimeSyncService {

    private final AnimeRepository animeRepository;
    private final CharacterRepository characterRepository;
    private final AnimeCharacterRepository animeCharacterRepository;
    private final GenreRepository genreRepository;
    private final AnimeGenreRepository animeGenreRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile boolean stopSync = false;
    private volatile int syncProgress = 0;
    private volatile int syncTotal = 0;
    private volatile int syncSuccess = 0;
    private volatile int syncFail = 0;
    private volatile int syncSkipped = 0;
    private volatile boolean isSyncing = false;

    private static final Map<String, String> GENRE_TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> TYPE_TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> STATUS_TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> SOURCE_TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> SEASON_TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> ROLE_TRANSLATIONS = new HashMap<>();
    private static final Set<String> FORBIDDEN_TAGS = new HashSet<>(Arrays.asList("百合", "色情", "女扮男装", "邪典", "恐怖", "成人角色"));

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

        ROLE_TRANSLATIONS.put("Main", "主角");
        ROLE_TRANSLATIONS.put("Supporting", "配角");
    }

    public AnimeSyncService(AnimeRepository animeRepository, CharacterRepository characterRepository,
                          AnimeCharacterRepository animeCharacterRepository, GenreRepository genreRepository,
                          AnimeGenreRepository animeGenreRepository) {
        this.animeRepository = animeRepository;
        this.characterRepository = characterRepository;
        this.animeCharacterRepository = animeCharacterRepository;
        this.genreRepository = genreRepository;
        this.animeGenreRepository = animeGenreRepository;
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

    private Set<Genre> translateAndSaveGenres(JsonNode node) {
        Set<Genre> genreSet = new HashSet<>();

        List<String> allGenreNames = new ArrayList<>();

        JsonNode genres = node.get("genres");
        if (genres != null && genres.isArray()) {
            for (JsonNode genre : genres) {
                String name = genre.get("name").asText();
                String translated = GENRE_TRANSLATIONS.getOrDefault(name, name);
                allGenreNames.add(translated);
            }
        }

        JsonNode themes = node.get("themes");
        if (themes != null && themes.isArray()) {
            for (JsonNode theme : themes) {
                String name = theme.get("name").asText();
                String translated = GENRE_TRANSLATIONS.getOrDefault(name, name);
                allGenreNames.add(translated);
            }
        }

        JsonNode demographics = node.get("demographics");
        if (demographics != null && demographics.isArray()) {
            for (JsonNode demo : demographics) {
                String name = demo.get("name").asText();
                String translated = GENRE_TRANSLATIONS.getOrDefault(name, name);
                allGenreNames.add(translated);
            }
        }

        for (String genreName : allGenreNames) {
            Genre genre = genreRepository.findByName(genreName).orElseGet(() -> {
                Genre newGenre = new Genre();
                newGenre.setName(genreName);
                return genreRepository.save(newGenre);
            });
            genreSet.add(genre);
        }

        return genreSet;
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

            anime.setGenres(translateAndSaveGenres(node));

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

    private String translateRole(String role) {
        return ROLE_TRANSLATIONS.getOrDefault(role, role);
    }

    public String cleanAnimeData() {
        List<Anime> allAnime = animeRepository.findAll();
        int deletedCount = 0;
        int cleanedCount = 0;

        for (Anime anime : allAnime) {
            boolean shouldDelete = false;

            if (anime.getGenres() != null) {
                for (Genre genre : anime.getGenres()) {
                    if (FORBIDDEN_TAGS.contains(genre.getName())) {
                        shouldDelete = true;
                        break;
                    }
                }
            }

            if (shouldDelete) {
                animeRepository.delete(anime);
                deletedCount++;
            } else {
                animeRepository.save(anime);
                cleanedCount++;
            }
        }

        return String.format("清理完成：删除 %d 条违规数据，清理 %d 条数据", deletedCount, cleanedCount);
    }

    @Transactional
    public String syncAnimeCharacters(Long animeId) {
        String url = String.format("https://api.jikan.moe/v4/anime/%d/characters", animeId);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getBody() == null) {
                return "API返回为空";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode dataArray = root.get("data");

            if (dataArray == null || dataArray.isEmpty()) {
                return "未找到该动漫的角色数据";
            }

            animeCharacterRepository.deleteByAnimeId(animeId);

            int addedCount = 0;
            int limit = Math.min(dataArray.size(), 5);

            for (int i = 0; i < limit; i++) {
                JsonNode node = dataArray.get(i);

                JsonNode characterInfo = node.get("character");
                if (characterInfo == null) {
                    continue;
                }

                JsonNode malIdNode = characterInfo.get("mal_id");
                if (malIdNode == null || malIdNode.isNull()) {
                    continue;
                }
                Long characterId = malIdNode.asLong();

                Character character = characterRepository.findById(characterId).orElse(null);

                if (character == null) {
                    character = new Character();
                    character.setId(characterId);
                }

                character.setName(getTextValue(characterInfo, "name"));
                character.setNameJp(getTextValue(characterInfo, "name_japanese"));

                JsonNode images = characterInfo.get("images");
                if (images != null) {
                    JsonNode jpg = images.get("jpg");
                    if (jpg != null) {
                        character.setImageUrl(getTextValue(jpg, "image_url"));
                    }
                }

                JsonNode favoritesNode = node.get("favorites");
                if (favoritesNode != null && !favoritesNode.isNull()) {
                    character.setFavorites(favoritesNode.asInt());
                }

                characterRepository.save(character);

                AnimeCharacter animeCharacter = new AnimeCharacter();
                animeCharacter.setAnimeId(animeId);
                animeCharacter.setCharacterId(characterId);

                JsonNode roleNode = node.get("role");
                if (roleNode != null && !roleNode.isNull()) {
                    animeCharacter.setRole(translateRole(roleNode.asText()));
                }

                animeCharacterRepository.save(animeCharacter);

                addedCount++;
            }

            return String.format("成功为动漫 %d 添加 %d 个角色", animeId, addedCount);
        } catch (Exception e) {
            e.printStackTrace();
            return "添加角色失败: " + e.getMessage();
        }
    }

    @Transactional
    public String syncAnimeGenres(Long animeId) {
        String url = String.format("https://api.jikan.moe/v4/anime/%d", animeId);

        try {
            Thread.sleep(500);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getBody() == null) {
                return "API返回为空";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");

            if (data == null) {
                return "未找到该动漫数据";
            }

            Set<Genre> genreSet = translateAndSaveGenres(data);

            animeGenreRepository.deleteByAnimeId(animeId);

            for (Genre genre : genreSet) {
                AnimeGenre animeGenre = new AnimeGenre();
                animeGenre.setAnimeId(animeId);
                animeGenre.setGenreId(genre.getId());
                animeGenreRepository.save(animeGenre);
            }

            return String.format("成功为动漫 %d 同步 %d 个题材", animeId, genreSet.size());
        } catch (Exception e) {
            e.printStackTrace();
            return "同步题材失败: " + e.getMessage();
        }
    }

    @Transactional
    public String syncAllAnimeGenres() {
        List<Anime> allAnime = animeRepository.findAll();
        int successCount = 0;
        int failCount = 0;
        int totalGenres = 0;

        for (Anime anime : allAnime) {
            String result = syncAnimeGenres(anime.getId());
            if (result.contains("成功")) {
                successCount++;
                try {
                    String[] parts = result.split(" ");
                    if (parts.length >= 4) {
                        totalGenres += Integer.parseInt(parts[3]);
                    }
                } catch (NumberFormatException e) {
                }
            } else {
                failCount++;
            }
        }

        return String.format("题材同步完成：成功 %d 个，失败 %d 个，共 %d 个题材关联", successCount, failCount, totalGenres);
    }

    public String syncAnimeById(Long animeId) {
        String url = String.format("https://api.jikan.moe/v4/anime/%d", animeId);

        try {
            Thread.sleep(500);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getBody() == null) {
                return "API返回为空";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");

            if (data == null || data.isNull()) {
                return "未找到ID为 " + animeId + " 的动漫数据";
            }

            Anime anime = parseAnime(data);
            if (anime != null) {
                anime.setId(animeId);

                Optional<Anime> existingAnime = animeRepository.findById(animeId);
                if (existingAnime.isPresent() && existingAnime.get().getCreatedAt() != null) {
                    anime.setCreatedAt(existingAnime.get().getCreatedAt());
                }

                animeRepository.save(anime);
                return String.format("成功同步动漫：%s (ID: %d)", anime.getTitle(), animeId);
            } else {
                return "解析动漫数据失败";
            }
        } catch (Exception e) {
            return "同步失败: " + e.getMessage();
        }
    }

    @Transactional
    public String syncAnimeYearFromApi() {
        List<Anime> animeWithoutYear = animeRepository.findByYearIsNull();
        if (animeWithoutYear == null || animeWithoutYear.isEmpty()) {
            return "没有找到年份为空的动漫";
        }

        stopSync = false;
        syncProgress = 0;
        syncTotal = animeWithoutYear.size();
        syncSuccess = 0;
        syncFail = 0;
        syncSkipped = 0;
        isSyncing = true;

        for (Anime anime : animeWithoutYear) {
            if (stopSync) {
                isSyncing = false;
                return String.format("同步已停止：成功 %d 个，失败 %d 个，跳过 %d 个（无年份数据），已处理 %d/%d", 
                    syncSuccess, syncFail, syncSkipped, syncProgress, syncTotal);
            }

            try {
                String url = String.format("https://api.jikan.moe/v4/anime/%d", anime.getId());
                Thread.sleep(500);

                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

                if (response.getBody() == null) {
                    syncFail++;
                    syncProgress++;
                    continue;
                }

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");

                if (data == null || data.isNull()) {
                    syncFail++;
                    syncProgress++;
                    continue;
                }

                JsonNode aired = data.get("aired");
                if (aired != null && !aired.isNull()) {
                    JsonNode prop = aired.get("prop");
                    if (prop != null && !prop.isNull()) {
                        JsonNode from = prop.get("from");
                        if (from != null && !from.isNull()) {
                            JsonNode yearNode = from.get("year");
                            if (yearNode != null && !yearNode.isNull()) {
                                Integer year = yearNode.asInt();
                                anime.setYear(year);
                                animeRepository.save(anime);
                                syncSuccess++;
                                syncProgress++;
                                continue;
                            }
                        }
                    }
                }
                syncSkipped++;
                syncProgress++;
            } catch (Exception e) {
                syncFail++;
                syncProgress++;
            }
        }

        isSyncing = false;
        return String.format("年份同步完成：成功 %d 个，失败 %d 个，跳过 %d 个（无年份数据）", syncSuccess, syncFail, syncSkipped);
    }

    public void stopSync() {
        this.stopSync = true;
    }

    public Map<String, Object> getSyncProgress() {
        Map<String, Object> progress = new HashMap<>();
        progress.put("isSyncing", isSyncing);
        progress.put("progress", syncProgress);
        progress.put("total", syncTotal);
        progress.put("success", syncSuccess);
        progress.put("fail", syncFail);
        progress.put("skipped", syncSkipped);
        progress.put("percentage", syncTotal > 0 ? (int) ((syncProgress * 100.0) / syncTotal) : 0);
        return progress;
    }

    @Transactional
    public String syncAnimeYearById(Long animeId) {
        Optional<Anime> optionalAnime = animeRepository.findById(animeId);
        if (optionalAnime.isEmpty()) {
            return "未找到ID为 " + animeId + " 的动漫";
        }

        Anime anime = optionalAnime.get();

        try {
            String url = String.format("https://api.jikan.moe/v4/anime/%d", animeId);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getBody() == null) {
                return "API返回为空";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");

            if (data == null || data.isNull()) {
                return "未找到ID为 " + animeId + " 的动漫数据";
            }

            JsonNode aired = data.get("aired");
            if (aired != null && !aired.isNull()) {
                JsonNode prop = aired.get("prop");
                if (prop != null && !prop.isNull()) {
                    JsonNode from = prop.get("from");
                    if (from != null && !from.isNull()) {
                        JsonNode yearNode = from.get("year");
                        if (yearNode != null && !yearNode.isNull()) {
                            Integer year = yearNode.asInt();
                            anime.setYear(year);
                            animeRepository.save(anime);
                            return String.format("成功为动漫 %d (%s) 更新年份为 %d", animeId, anime.getTitle(), year);
                        }
                    }
                }
            }
            return String.format("动漫 %d (%s) 未找到年份数据", animeId, anime.getTitle());
        } catch (Exception e) {
            e.printStackTrace();
            return "同步失败: " + e.getMessage();
        }
    }
}
