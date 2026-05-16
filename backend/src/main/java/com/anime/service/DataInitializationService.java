package com.anime.service;

import com.anime.entity.Anime;
import com.anime.entity.Character;
import com.anime.repository.AnimeRepository;
import com.anime.repository.CharacterRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class DataInitializationService implements CommandLineRunner {

    private final AnimeRepository animeRepository;
    private final CharacterRepository characterRepository;

    public DataInitializationService(AnimeRepository animeRepository, CharacterRepository characterRepository) {
        this.animeRepository = animeRepository;
        this.characterRepository = characterRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (animeRepository.count() > 0) {
            return;
        }

        List<Anime> animeList = createSampleAnime();
        animeRepository.saveAll(animeList);

        List<Character> characters = createSampleCharacters();
        characterRepository.saveAll(characters);
    }

    private List<Anime> createSampleAnime() {
        return Arrays.asList(
            createAnime("進撃の巨人", "Attack on Titan", 
                "人类与巨人的殊死搏斗，揭开世界的真相。",
                "https://cdn.myanimelist.net/images/anime/10/47347.jpg", 
                9.1, 87, "TV", "春季", 2013, "已完结", "Wit Studio", "动作,剧情,奇幻", "漫画"),
            
            createAnime("鬼滅の刃", "Demon Slayer",
                "炭治郎的妹妹变成鬼，他决定踏上灭鬼之路。",
                "https://cdn.myanimelist.net/images/anime/1286/99889.jpg",
                8.7, 26, "TV", "春季", 2019, "已完结", "ufotable", "动作,奇幻,超自然", "漫画"),
            
            createAnime("呪術廻戦", "Jujutsu Kaisen",
                "高中生虎杖悠仁吞下诅咒之王的手指，进入咒术世界。",
                "https://cdn.myanimelist.net/images/anime/1171/109222.jpg",
                8.8, 24, "TV", "秋季", 2020, "已完结", "MAPPA", "动作,奇幻,超自然", "漫画"),
            
            createAnime("Spy×Family", "Spy x Family",
                "间谍、杀手、超能力者组成的奇特家庭。",
                "https://cdn.myanimelist.net/images/anime/1441/122795.jpg",
                8.9, 25, "TV", "春季", 2022, "已完结", "Wit Studio", "动作,喜剧,奇幻", "漫画"),
            
            createAnime("ONE PIECE", "One Piece",
                "路飞踏上寻找海贼王的冒险之旅。",
                "https://cdn.myanimelist.net/images/anime/6/73245.jpg",
                8.7, 1000, "TV", "秋季", 1999, "播出中", "Toei Animation", "动作,冒险,喜剧", "漫画"),
            
            createAnime("葬送のフリーレン", "Frieren",
                "勇者们的冒险结束后的故事，精灵法师芙莉莲的千年之旅。",
                "https://cdn.myanimelist.net/images/anime/1015/138006.jpg",
                9.2, 28, "TV", "秋季", 2023, "已完结", "Madhouse", "冒险,剧情,奇幻", "漫画"),
            
            createAnime("呪術廻戦 0", "Jujutsu Kaisen 0",
                "乙骨忧太与祈本里香的爱情故事。",
                "https://cdn.myanimelist.net/images/anime/1793/122216.jpg",
                8.8, 1, "MOVIE", "冬季", 2021, "已完结", "MAPPA", "动作,奇幻,超自然", "漫画"),
            
            createAnime("ハイキュー!!", "Haikyu!!",
                "身材矮小的日向翔阳追逐排球梦。",
                "https://cdn.myanimelist.net/images/anime/7/76049.jpg",
                8.6, 85, "TV", "春季", 2014, "已完结", "Production I.G", "运动,剧情,喜剧", "漫画"),
            
            createAnime("約束のネバーランド", "The Promised Neverland",
                "孤儿院孩子们策划的惊心动魄的逃脱计划。",
                "https://cdn.myanimelist.net/images/anime/1951/117413.jpg",
                8.4, 37, "TV", "冬季", 2019, "已完结", "CloverWorks", "悬疑,剧情,惊悚", "漫画"),
            
            createAnime("ヴァイオレット・エヴァーガーデン", "Violet Evergarden",
                "战争结束后，写信代笔人寻找爱与理解。",
                "https://cdn.myanimelist.net/images/anime/1795/95057.jpg",
                8.9, 13, "TV", "冬季", 2018, "已完结", "Kyoto Animation", "剧情,奇幻,日常", "轻小说"),
            
            createAnime("盾の勇者", "The Rising of the Shield Hero",
                "被召唤为盾之勇者的青年。",
                "https://cdn.myanimelist.net/images/anime/6/87249.jpg",
                7.8, 25, "TV", "冬季", 2019, "已完结", "Kinema Citrus", "动作,奇幻,剧情", "轻小说"),
            
            createAnime("チェンソーマン", "Chainsaw Man",
                "电次成为恶魔猎人，与恶魔波奇塔融合。",
                "https://cdn.myanimelist.net/images/anime/1806/126216.jpg",
                8.7, 12, "TV", "秋季", 2022, "已完结", "MAPPA", "动作,奇幻,恐怖", "漫画"),
            
            createAnime("転生したらスライムだった件", "That Time I Got Reincarnated as a Slime",
                "程序员转生为史莱姆，在异世界建立国家。",
                "https://cdn.myanimelist.net/images/anime/9/94478.jpg",
                8.1, 48, "TV", "秋季", 2018, "已完结", "8bit", "动作,奇幻,喜剧", "轻小说"),
            
            createAnime("薬屋のひとりごと", "The Apothecary Diaries",
                "在皇宫中运用药学知识解决事件。",
                "https://cdn.myanimelist.net/images/anime/1011/132694.jpg",
                8.4, 24, "TV", "秋季", 2023, "已完结", "Owl Eyes", "悬疑,剧情,奇幻", "轻小说"),
            
            createAnime("ブルーロック", "Blue Lock",
                "日本足球青训改革计划，300人争夺前锋之位。",
                "https://cdn.myanimelist.net/images/anime/1258/126929.jpg",
                8.4, 24, "TV", "秋季", 2022, "已完结", "MAPPA", "运动,剧情,惊悚", "漫画"),
            
            createAnime("よりservice", "Oshi no Ko",
                "妇产科医生转生为偶像的孩子。",
                "https://cdn.myanimelist.net/images/anime/1524/135345.jpg",
                8.9, 18, "TV", "春季", 2023, "已完结", "Doga Kobo", "剧情,悬疑,喜剧", "漫画"),
            
            createAnime("東京リベンジャーズ", "Tokyo Revengers",
                "花垣武道穿越时空拯救恋人。",
                "https://cdn.myanimelist.net/images/anime/1052/11053.jpg",
                7.5, 24, "TV", "春季", 2021, "已完结", "Studio Pierrot", "动作,剧情,奇幻", "漫画"),
            
            createAnime("無職転生", "Mushoku Tensei",
                "34岁尼特转生到异世界的冒险。",
                "https://cdn.myanimelist.net/images/anime/1015/122795.jpg",
                8.5, 23, "TV", "冬季", 2021, "已完结", "Studio Bind", "剧情,奇幻,冒险", "轻小说"),
            
            createAnime("áticas", "Mob Psycho 100",
                "拥有超能力的幽灵高中生爆浦。",
                "https://cdn.myanimelist.net/images/anime/8/77857.jpg",
                8.5, 37, "TV", "夏季", 2016, "已完结", "BONES", "动作,喜剧,超自然", "网络漫画"),
            
            createAnime("が必要", "Vinland Saga",
                "维京战士托尔兹的复仇与成长。",
                "https://cdn.myanimelist.net/images/anime/1000/110059.jpg",
                8.8, 24, "TV", "夏季", 2019, "已完结", "WIT Studio", "动作,冒险,剧情", "漫画")
        );
    }

    private Anime createAnime(String title, String titleJp, String synopsis, String imageUrl,
                               Double score, Integer episodes, String type, String season,
                               Integer year, String status, String studios, String genres, String source) {
        Anime anime = new Anime();
        anime.setTitle(title);
        anime.setTitleJp(titleJp);
        anime.setSynopsis(synopsis);
        anime.setImageUrl(imageUrl);
        anime.setScore(score);
        anime.setEpisodes(episodes);
        anime.setType(type);
        anime.setSeason(season);
        anime.setYear(year);
        anime.setStatus(status);
        anime.setStudios(studios);
        anime.setGenres(genres);
        anime.setSource(source);
        return anime;
    }

    private List<Character> createSampleCharacters() {
        return Arrays.asList(
            createCharacter(1L, "艾伦·耶格尔", "Eren Yeager", "Protagonist", "https://cdn.myanimelist.net/images/characters/9/204811.jpg"),
            createCharacter(1L, "三笠·阿克曼", "Mikasa Ackerman", "Main", "https://cdn.myanimelist.net/images/characters/6/227149.jpg"),
            createCharacter(1L, "阿尔敏·亚鲁雷特", "Armin Arlert", "Main", "https://cdn.myanimelist.net/images/characters/9/227151.jpg"),
            createCharacter(2L, "灶门炭治郎", "Tanjiro Kamado", "Protagonist", "https://cdn.myanimelist.net/images/characters/9/108941.jpg"),
            createCharacter(2L, "灶门祢豆子", "Nezuko Kamado", "Main", "https://cdn.myanimelist.net/images/characters/5/112407.jpg"),
            createCharacter(2L, "我妻善逸", "Zenitsu Agatsuma", "Main", "https://cdn.myanimelist.net/images/characters/5/112409.jpg"),
            createCharacter(3L, "虎杖悠仁", "Yuji Itadori", "Protagonist", "https://cdn.myanimelist.net/images/characters/9/120136.jpg"),
            createCharacter(3L, "伏黑惠", "Megumi Fushiguro", "Main", "https://cdn.myanimelist.net/images/characters/5/120138.jpg"),
            createCharacter(3L, "钉崎野蔷薇", "Nobara Kugisaki", "Main", "https://cdn.myanimelist.net/images/characters/11/120140.jpg"),
            createCharacter(4L, "劳埃德·福杰", "Loid Forger", "Protagonist", "https://cdn.myanimelist.net/images/characters/9/128566.jpg"),
            createCharacter(4L, "约尔·福杰", "Yor Forger", "Main", "https://cdn.myanimelist.net/images/characters/3/128570.jpg"),
            createCharacter(4L, "安妮亚·福杰", "Anya Forger", "Main", "https://cdn.myanimelist.net/images/characters/7/128572.jpg")
        );
    }

    private Character createCharacter(Long animeId, String name, String nameJp, String role, String imageUrl) {
        Character character = new Character();
        character.setAnimeId(animeId);
        character.setName(name);
        character.setNameJp(nameJp);
        character.setRole(role);
        character.setImageUrl(imageUrl);
        return character;
    }
}