package com.animelist.model;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "season_genre")
public class SeasonGenre {

    @EmbeddedId
    private SeasonGenreId id = new SeasonGenreId();

    @JsonIgnore
    @ManyToOne
    @MapsId("idSeason")
    @JoinColumn(name = "id_season")
    private Season season;

    @ManyToOne
    @MapsId("idGenre")
    @JoinColumn(name = "id_genre")
    private Genre genre;

    @Column(name = "is_main_genre", nullable = false)
    private Boolean isMainGenre; // 1 = main, 0 = subsidiary

    public SeasonGenre(Season season, Genre genre, Boolean isMainGenre) {
        this.season      = season;
        this.genre       = genre;
        this.isMainGenre = isMainGenre;
        this.id          = new SeasonGenreId(season.getId(), genre.getId());
    }

    public SeasonGenre() {}

    public SeasonGenreId getId()                        { return id; }
    public void          setId(SeasonGenreId id)       { this.id = id; }

    public Season getSeason()                           { return season; }
    public void   setSeason(Season season)             { this.season = season; }

    public Genre getGenre()                             { return genre; }
    public void  setGenre(Genre genre)                 { this.genre = genre; }

    public Boolean getIsMainGenre()                           { return isMainGenre; }
    public void    setIsMainGenre(Boolean isMainGenre)     { this.isMainGenre = isMainGenre; }
}
