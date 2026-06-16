package com.anime.repository;

import com.anime.entity.Anime;
import com.anime.entity.Genre;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public class AnimeSpecifications {

    public static Specification<Anime> hasType(String type) {
        return (root, query, cb) -> {
            if (type == null || type.isEmpty()) return null;
            if ("other".equalsIgnoreCase(type) || "\u5176\u4ed6".equals(type)) {
                return cb.and(
                    cb.notEqual(root.get("type"), "TV\u52a8\u753b"),
                    cb.notEqual(root.get("type"), "\u5267\u573a\u7248"),
                    cb.notEqual(root.get("type"), "OVA")
                );
            }
            return cb.equal(root.get("type"), type);
        };
    }

    public static Specification<Anime> hasSource(String source) {
        return (root, query, cb) ->
            source == null || source.isEmpty() ? null : cb.equal(root.get("source"), source);
    }

    public static Specification<Anime> hasSeason(String season) {
        return (root, query, cb) ->
            season == null || season.isEmpty() ? null : cb.equal(root.get("season"), season);
    }

    public static Specification<Anime> hasYear(Integer year) {
        return (root, query, cb) ->
            year == null ? null : cb.equal(root.get("year"), year);
    }

    public static Specification<Anime> hasStatus(String status) {
        return (root, query, cb) ->
            status == null || status.isEmpty() ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Anime> hasGenre(String genreName) {
        return (root, query, cb) -> {
            if (genreName == null || genreName.isEmpty()) return null;
            query.distinct(true);
            Join<Anime, Genre> genres = root.join("genres");
            return cb.equal(genres.get("name"), genreName);
        };
    }

    public static Specification<Anime> hasScoreNotNull() {
        return (root, query, cb) -> cb.isNotNull(root.get("score"));
    }

    public static Specification<Anime> hasYearBetween(Integer startYear, Integer endYear) {
        return (root, query, cb) -> {
            if (startYear == null && endYear == null) return null;
            if (startYear != null && endYear != null) {
                return cb.between(root.get("year"), startYear, endYear);
            } else if (startYear != null) {
                return cb.greaterThanOrEqualTo(root.get("year"), startYear);
            } else {
                return cb.lessThanOrEqualTo(root.get("year"), endYear);
            }
        };
    }

    public static Specification<Anime> hasImageUrl() {
        return (root, query, cb) -> cb.and(
            cb.isNotNull(root.get("imageUrl")),
            cb.notEqual(root.get("imageUrl"), "")
        );
    }

    public static Specification<Anime> hasTitleJp() {
        return (root, query, cb) -> cb.and(
            cb.isNotNull(root.get("titleJp")),
            cb.notEqual(root.get("titleJp"), "")
        );
    }

    public static Specification<Anime> scoreGreaterThanZero() {
        return (root, query, cb) -> cb.and(
            cb.isNotNull(root.get("score")),
            cb.notEqual(root.get("score"), 0.0)
        );
    }
}
