package com.animelist.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;

@Entity
@Table(name = "seasons")
public class Season {

    @Transient
    private Integer idFranchise;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_season")
    private Integer id;

    @Column(name = "id_serie", nullable = false)
    private Integer idSerie;

    // int(11) → Integer
    @Column(name = "id_elt_kitsu")
    private Integer idKitsu;

    @Column(name = "id_elt_livechart")
    private Integer idLivechart;

    @Column(name = "id_elt_anisearch")
    private Integer idAnisearch;

    @Column(name = "watch_index")
    private Integer watchIndex;

    @Column(name = "saga_principale")
    private Boolean sagaPrincipale;

    // varchar → String
    @Column(name = "nom_fr")
    private String nomFr;

    @Column(name = "nom_orig")
    private String nomOrig;

    @Column(name = "image", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_baniere", columnDefinition = "TEXT")
    private String imageBaniereUrl;

    @Column(name = "status")
    private String status;

    // decimal(3,2) → BigDecimal
    @Column(name = "note", precision = 3, scale = 2)
    private BigDecimal note;

    @Column(name = "note_perso", precision = 3, scale = 2)
    private BigDecimal personalNote;

    // tinyint(2) → Byte
    @Column(name = "jour_sortie")
    private Byte jourPremiere;

    @Column(name = "mois_sortie")
    private Byte moisPremiere;

    // smallint(4) → Short
    @Column(name = "annee_sortie")
    private Short anneePremiere;

    // tinyint(2) → Byte
    @Column(name = "jour_next_ep")
    private Byte jourNextEp;

    @Column(name = "mois_next_ep")
    private Byte moisNextEp;

    // smallint(4) → Short
    @Column(name = "annee_next_ep")
    private Short anneeNextEp;

    // varchar → String
    @Column(name = "heure_next_ep")
    private String heureNextEp;

    // int(11) → Integer
    @Column(name = "nb_episodes")
    private Integer nbEpisodes;

    @Column(name = "duree_moyenne")
    private Integer dureeMoyenne;

    @Column(name = "duree_totale")
    private Integer dureeTotale;

    @Column(name = "site", columnDefinition = "TEXT")
    private String siteOfficiel;

    @Column(name = "format")
    private String format;

    @JsonIgnore
    @OneToMany(mappedBy = "season", fetch = FetchType.EAGER,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeasonGenre> seasonGenres = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "season", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("watchIndex ASC")
    private List<Episode> episodes = new ArrayList<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name               = "season_studio",
        joinColumns        = @JoinColumn(name = "id_season"),
        inverseJoinColumns = @JoinColumn(name = "id_studio")
    )
    private List<Studio> studio = new ArrayList<>();

    public Season(
        String franchiseId,
        String liveChartSeasonId,
        String kitsuId,
        String anisearchId,
        String romaji,
        String english,
        String dateStr,       // ex: "Sep 29, 2023"
        String studio,
        String nbEpisodes,
        String dureeMoyenne,
        String format
    ) {
        this.idFranchise  = parseOrNull(franchiseId);
        this.idLivechart  = parseOrNull(liveChartSeasonId);
        this.idKitsu      = parseOrNull(kitsuId);
        this.idAnisearch  = parseOrNull(anisearchId);
        this.nomOrig      = romaji;
        this.nomFr        = english;
        this.format       = format;
        this.nbEpisodes   = parseOrNull(nbEpisodes);
        this.dureeMoyenne = parseOrNull(dureeMoyenne);
        // Studio : à rattacher via setStudio() après résolution en base
        // Date : parsing de "Sep 29, 2023"
        if (dateStr != null) {
            try {
                java.time.LocalDate d = java.time.LocalDate.parse(
                    dateStr.trim(),
                    java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH)
                );
                this.jourPremiere  = (byte) d.getDayOfMonth();
                this.moisPremiere  = (byte) d.getMonthValue();
                this.anneePremiere = (short) d.getYear();
            } catch (Exception ignored) {}
        }
    }

    private static Integer parseOrNull(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Integer.parseInt(val.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return null; }
    }

    public Season() {}

    // ── Utilitaires ───────────────────────────────────────────────────────────

    public String getDatePremiere() {
        String j = (jourPremiere != null && jourPremiere > 0) ? String.format("%02d", jourPremiere) : "--";
        String m = (moisPremiere != null && moisPremiere > 0) ? String.format("%02d", moisPremiere) : "--";
        String a = (anneePremiere != null && anneePremiere > 0) ? String.valueOf(anneePremiere) : "--";
        return j + "/" + m + "/" + a;
    }

    public String getDateNextEp() {
        String j = (jourNextEp != null && jourNextEp > 0) ? String.format("%02d", jourNextEp) : "--";
        String m = (moisNextEp != null && moisNextEp > 0) ? String.format("%02d", moisNextEp) : "--";
        String a = (anneeNextEp != null && anneeNextEp > 0) ? String.valueOf(anneeNextEp) : "--";
        return j + "/" + m + "/" + a;
    }

    @JsonProperty("genresPrincipal")
    public List<Genre> getMainGenres() {
        return seasonGenres.stream()
                .filter(sg -> sg.getIsMainGenre() != null && sg.getIsMainGenre())
                .map(SeasonGenre::getGenre)
                .toList();
    }

    @JsonProperty("genresSecondaires")
    public List<Genre> getSecondaryGenres() {
        return seasonGenres.stream()
                .filter(sg -> sg.getIsMainGenre() != null && !sg.getIsMainGenre())
                .map(SeasonGenre::getGenre)
                .toList();
    }

    @JsonProperty("genres")
    public List<Genre> getAllGenres() {
        return seasonGenres.stream().map(SeasonGenre::getGenre).toList();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Integer getIdFranchise()                                 { return idFranchise; }

    public Integer getId()                                          { return id; }
    public void    setId(Integer id)                                { this.id = id; }

    public Integer getIdSerie()                                     { return idSerie; }
    public void    setIdSerie(Integer idSerie)                      { this.idSerie = idSerie; }

    public Integer getIdKitsu()                                     { return idKitsu; }
    public void    setIdKitsu(Integer idKitsu)                      { this.idKitsu = idKitsu; }

    public Integer getIdLivechart()                                 { return idLivechart; }
    public void    setIdLivechart(Integer idLivechart)              { this.idLivechart = idLivechart; }

    public Integer getIdAnisearch()                                 { return idAnisearch; }
    public void    setIdAnisearch(Integer idAnisearch)              { this.idAnisearch = idAnisearch; }

    public Integer getWatchIndex()                                  { return watchIndex; }
    public void    setWatchIndex(Integer watchIndex)                { this.watchIndex = watchIndex; }

    public Boolean isSagaPrincipale()                               { return sagaPrincipale; }
    public void    setSagaPrincipale(Boolean sagaPrincipale)        { this.sagaPrincipale = sagaPrincipale; }

    public String getNomFr()                                        { return nomFr; }
    public void   setNomFr(String nomFr)                            { this.nomFr = nomFr; }

    public String getNomOrig()                                      { return nomOrig; }
    public void   setNomOrig(String nomOrig)                        { this.nomOrig = nomOrig; }

    public String getImageUrl()                                     { return imageUrl; }
    public void   setImageUrl(String imageUrl)                      { this.imageUrl = imageUrl; }

    public String getImageBaniereUrl()                              { return imageBaniereUrl; }
    public void   setImageBaniereUrl(String v)                      { this.imageBaniereUrl = v; }

    public String getStatus()                                       { return status; }
    public void   setStatus(String status)                          { this.status = status; }

    public BigDecimal getNote()                                     { return note; }
    public void       setNote(BigDecimal note)                      { this.note = note; }

    public BigDecimal getPersonalNote()                             { return personalNote; }
    public void       setPersonalNote(BigDecimal personalNote)      { this.personalNote = personalNote; }

    public Byte getJourPremiere()                                   { return jourPremiere; }
    public void setJourPremiere(Byte v)                             { this.jourPremiere = v; }

    public Byte getMoisPremiere()                                   { return moisPremiere; }
    public void setMoisPremiere(Byte v)                             { this.moisPremiere = v; }

    public Short getAnneePremiere()                                 { return anneePremiere; }
    public void  setAnneePremiere(Short v)                          { this.anneePremiere = v; }

    public Byte getJourNextEp()                                     { return jourNextEp; }
    public void setJourNextEp(Byte jourNextEp)                      { this.jourNextEp = jourNextEp; }

    public Byte getMoisNextEp()                                     { return moisNextEp; }
    public void setMoisNextEp(Byte moisNextEp)                      { this.moisNextEp = moisNextEp; }

    public Short getAnneeNextEp()                                   { return anneeNextEp; }
    public void  setAnneeNextEp(Short anneeNextEp)                  { this.anneeNextEp = anneeNextEp; }

    public String getHeureNextEp()                                  { return heureNextEp; }
    public void   setHeureNextEp(String heureNextEp)                { this.heureNextEp = heureNextEp; }

    public Integer getNbEpisodes()                                  { return nbEpisodes; }
    public void    setNbEpisodes(Integer nbEpisodes)                { this.nbEpisodes = nbEpisodes; }

    public Integer getDureeMoyenne()                                { return dureeMoyenne; }
    public void    setDureeMoyenne(Integer dureeMoyenne)            { this.dureeMoyenne = dureeMoyenne; }

    public Integer getDureeTotale()                                 { return dureeTotale; }
    public void    setDureeTotale(Integer dureeTotale)              { this.dureeTotale = dureeTotale; }

    public String getSiteOfficiel()                                 { return siteOfficiel; }
    public void   setSiteOfficiel(String siteOfficiel)              { this.siteOfficiel = siteOfficiel; }

    public String getFormat()                                       { return format; }
    public void   setFormat(String format)                          { this.format = format; }

    public List<SeasonGenre> getSeasonGenres()                      { return seasonGenres; }
    public void              setSeasonGenres(List<SeasonGenre> sg)  { this.seasonGenres = sg; }

    public List<Episode> getEpisodes()                              { return episodes; }
    public void          setEpisodes(List<Episode> episodes)        { this.episodes = episodes; }

    public List<Studio> getStudio()                                 { return studio; }
    public void         setStudio(List<Studio> studio)              { this.studio = studio; }
}