package com.animelist.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "series")
public class Serie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_serie")
    private Integer id;

    // int(11) → Integer
    @Column(name = "id_first_elt_kitsu")
    private Integer idFirstEltKitsu;

    @Column(name = "id_first_elt_livechart")
    private Integer idFirstEltLiveChart;

    @Column(name = "id_first_elt_anisearch")
    private Integer idFirstEltAnisearch;

    @Column(name = "id_franchise")
    private Integer idFranchise;

    // varchar → String
    @Column(name = "nom_fr", nullable = false)
    private String nomFr;

    @Column(name = "nom_orig")
    private String nomOrig;

    @Column(name = "image", columnDefinition = "TEXT")
    private String image;

    @Column(name = "image_baniere", columnDefinition = "TEXT")
    private String imageBaniere;

    // tinyint(1) → Boolean
    @Column(name = "favorite")
    private Boolean favorite = false;

    @Column(name = "to_watch")
    private Boolean toWatch  = false;

    // timestamp → LocalDateTime (géré en lecture seule — valeur auto en BDD)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_serie", insertable = false, updatable = false)
    private List<Season> seasons = new ArrayList<>();

    @Transient
    private BigDecimal note;

    @Transient
    private String date;

    // ── Champs calculés à partir de la trame principale (saga_principale = true) ──

    /** Status de la dernière saison de la trame principale (par date de sortie). */
    @Transient
    private String status;

    /** Somme du nombre d'épisodes de toutes les saisons de la trame principale. */
    @Transient
    private Integer episodes;

    /** Genres (tous confondus) de l'ensemble des saisons de la trame principale. */
    @Transient
    private List<String> genres;

    /** Genres principaux de l'ensemble des saisons de la trame principale. */
    @Transient
    private List<String> genresPrincipal;

    /** Genres secondaires de l'ensemble des saisons de la trame principale. */
    @Transient
    private List<String> genresSecondaires;

    public Serie() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Integer getId()                              { return id; }
    public void    setId(Integer id)                    { this.id = id; }

    public Integer getIdFirstEltKitsu()                 { return idFirstEltKitsu; }
    public void    setIdFirstEltKitsu(Integer v)        { this.idFirstEltKitsu = v; }

    public Integer getIdFirstEltLiveChart()             { return idFirstEltLiveChart; }
    public void    setIdFirstEltLiveChart(Integer v)    { this.idFirstEltLiveChart = v; }

    public Integer getIdFirstEltAnisearch()             { return idFirstEltAnisearch; }
    public void    setIdFirstEltAnisearch(Integer v)    { this.idFirstEltAnisearch = v; }

    public Integer getIdFranchise()                     { return idFranchise; }
    public void    setIdFranchise(Integer v)            { this.idFranchise = v; }

    public String getNomFr()                            { return nomFr; }
    public void   setNomFr(String v)                    { this.nomFr = v; }

    public String getNomOrig()                          { return nomOrig; }
    public void   setNomOrig(String v)                  { this.nomOrig = v; }

    public String getImage()                            { return image; }
    public void   setImage(String v)                    { this.image = v; }

    public String getImageBaniere()                     { return imageBaniere; }
    public void   setImageBaniere(String v)             { this.imageBaniere = v; }

    public Boolean getFavorite()                         { return favorite; }
    public void    setFavorite(Boolean v)                { this.favorite = v; }

    public Boolean getToWatch()                         { return toWatch; }
    public void    setToWatch(Boolean v)                { this.toWatch = v; }

    public LocalDateTime getCreatedAt()                 { return createdAt; }
    public LocalDateTime getUpdatedAt()                 { return updatedAt; }

    public List<Season> getSeasons()                    { return seasons; }
    public void         setSeasons(List<Season> v)      { this.seasons = v; }

    public BigDecimal getNote()                         { return note; }
    public void       setNote(BigDecimal note)          { this.note = note; }

    public String getDate()                             { return date; }
    public void   setDate(String date)                  { this.date = date; }

    public String getStatus()                           { return status; }
    public void   setStatus(String status)               { this.status = status; }

    public Integer getEpisodes()                        { return episodes; }
    public void    setEpisodes(Integer episodes)         { this.episodes = episodes; }

    public List<String> getGenres()                     { return genres; }
    public void         setGenres(List<String> genres)  { this.genres = genres; }

    public List<String> getGenresPrincipal()                          { return genresPrincipal; }
    public void         setGenresPrincipal(List<String> v)            { this.genresPrincipal = v; }

    public List<String> getGenresSecondaires()                        { return genresSecondaires; }
    public void         setGenresSecondaires(List<String> v)          { this.genresSecondaires = v; }

    /** Alias JSON "visionne" du champ existant to_watch (aucune donnée supplémentaire). */
    @JsonProperty("visionne")
    public Boolean getVisionne()                        { return toWatch; }
}