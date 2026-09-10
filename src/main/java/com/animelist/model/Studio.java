package com.animelist.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "studios")
public class Studio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_studio")
    private Integer id;

    @Column(name = "nom", unique = true, nullable = false)
    private String nom;

    @JsonIgnore
    @ManyToMany(mappedBy = "studio")
    private List<Season> seasons = new ArrayList<>();

    public Studio() {}

    public Studio(String nom) { this.nom = normalize(nom); }

    public Integer getId()               { return id; }
    public void    setId(Integer id)    { this.id = id; }

    public String getNom()               { return nom; }
    public void   setNom(String nom)    { this.nom = normalize(nom); }

    public List<Season> getSeasons()                { return seasons; }
    public void         setSeasons(List<Season> s) { this.seasons = s; }

    // ── Normalisation ───────────────────────────────────────────────────────
    // Garantit qu'un même studio (peu importe la casse / espaces fournis par
    // une source externe comme Kitsu) est toujours stocké sous une forme unique
    // en base, ex: "MADHOUSE", "madhouse", "Madhouse " → "Madhouse".
    @PrePersist
    @PreUpdate
    private void normalizeBeforeSave() {
        this.nom = normalize(this.nom);
    }

    public static String normalize(String nom) {
        if (nom == null) return null;
        String collapsed = nom.trim().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) return collapsed;

        StringBuilder sb = new StringBuilder(collapsed.length());
        boolean startOfWord = true;
        for (char c : collapsed.toCharArray()) {
            if (Character.isWhitespace(c)) {
                startOfWord = true;
                sb.append(c);
            } else if (startOfWord) {
                sb.append(Character.toUpperCase(c));
                startOfWord = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}