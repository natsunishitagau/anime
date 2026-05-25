package com.anime.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import java.util.Set;

@Entity
@Table(name = "characters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"animes"})
public class Character {
    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_jp")
    private String nameJp;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "favorites")
    private Integer favorites;

    @JsonIgnore
    @ManyToMany(mappedBy = "characters")
    private Set<Anime> animes;
}
