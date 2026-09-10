package com.animelist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SeasonGenreId implements Serializable {

    @Column(name = "id_season")
    private Integer idSeason;

    @Column(name = "id_genre")
    private Integer idGenre;

    public SeasonGenreId() {}

    public SeasonGenreId(Integer idSeason, Integer idGenre) {
        this.idSeason = idSeason;
        this.idGenre  = idGenre;
    }

    public Integer getIdSeason()                        { return idSeason; }
    public void    setIdSeason(Integer idSeason)       { this.idSeason = idSeason; }

    public Integer getIdGenre()                         { return idGenre; }
    public void    setIdGenre(Integer idGenre)         { this.idGenre = idGenre; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SeasonGenreId)) return false;
        SeasonGenreId that = (SeasonGenreId) o;
        return Objects.equals(idSeason, that.idSeason) && Objects.equals(idGenre, that.idGenre);
    }

    @Override
    public int hashCode() { return Objects.hash(idSeason, idGenre); }
}