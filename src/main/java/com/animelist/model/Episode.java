package com.animelist.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "episodes")
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_episode")
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_season", nullable = false)
    private Season season;

    @Column(name = "id_kitsu")
    private Integer idKitsu;

    @Column(name = "watch_index")
    private Integer watchIndex;

    @Column(name = "nom_fr")
    private String nomFr;

    @Column(name = "nom_orig")
    private String nomOrig;

    @Column(name = "image", columnDefinition = "TEXT")
    private String image;

    @Column(name = "jour_sortie")
    private Byte jourSortie;

    @Column(name = "mois_sortie")
    private Byte moisSortie;

    @Column(name = "annee_sortie")
    private Short anneeSortie;

    @Column(name = "duree")
    private Integer duree;

    @Column(name = "video_link", columnDefinition = "TEXT")
    private String videoLink;

    @Column(name = "temps_viso")
    private Integer tempsViso;

    public Episode() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Integer getId()                          { return id; }
    public void    setId(Integer id)               { this.id = id; }

    public Season getSeason()                       { return season; }
    public void   setSeason(Season season)         { this.season = season; }

    public Integer getIdKitsu()                     { return idKitsu; }
    public void    setIdKitsu(Integer idKitsu)     { this.idKitsu = idKitsu; }

    public Integer getWatchIndex()                  { return watchIndex; }
    public void    setWatchIndex(Integer v)        { this.watchIndex = v; }

    public String getNomFr()                        { return nomFr; }
    public void   setNomFr(String nomFr)           { this.nomFr = nomFr; }

    public String getNomOrig()                      { return nomOrig; }
    public void   setNomOrig(String nomOrig)       { this.nomOrig = nomOrig; }

    public String getImage()                        { return image; }
    public void   setImage(String image)           { this.image = image; }

    public Byte getJourSortie()                     { return jourSortie; }
    public void setJourSortie(Byte v)              { this.jourSortie = v; }

    public Byte getMoisSortie()                     { return moisSortie; }
    public void setMoisSortie(Byte v)              { this.moisSortie = v; }

    public Short getAnneeSortie()                   { return anneeSortie; }
    public void  setAnneeSortie(Short v)           { this.anneeSortie = v; }

    public Integer getDuree()                       { return duree; }
    public void    setDuree(Integer duree)         { this.duree = duree; }

    public String getVideoLink()                    { return videoLink; }
    public void   setVideoLink(String videoLink)   { this.videoLink = videoLink; }

    public Integer getTempsViso()                   { return tempsViso; }
    public void    setTempsViso(Integer tempsViso) { this.tempsViso = tempsViso; }
}