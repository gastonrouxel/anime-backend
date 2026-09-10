package com.animelist.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "genres")
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_genre")
    private Integer id;

    @Column(name = "nom", unique = true, nullable = false)
    private String nom;

    @JsonIgnore
    @OneToMany(mappedBy = "genre", fetch = FetchType.LAZY)
    private List<SeasonGenre> seasonGenres = new ArrayList<>();

    public Genre() {}

    public Genre(String nom) { this.nom = nom; }

    public Integer getId()              { return id; }
    public void    setId(Integer id)   { this.id = id; }

    public String getNom()              { return nom; }
    public void   setNom(String nom)   { this.nom = nom; }

    public List<SeasonGenre> getSeasonGenres()                      { return seasonGenres; }
    public void              setSeasonGenres(List<SeasonGenre> sg) { this.seasonGenres = sg; }
}
