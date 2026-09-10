package com.animelist.model;

/**
 * DTO retourné par le scraper LiveChart — pas une entité JPA.
 */
public class LiveChartResult {

    private String id;
    private String nom;
    private String titleExtra;  // titre original (japonais etc.)
    private String src;         // URL du poster

    public LiveChartResult() {}

    public LiveChartResult(String id, String nom, String titleExtra, String src) {
        this.id         = id;
        this.nom        = nom;
        this.titleExtra = titleExtra;
        this.src        = src;
    }

    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }

    public String getNom()                      { return nom; }
    public void   setNom(String nom)            { this.nom = nom; }

    public String getTitleExtra()               { return titleExtra; }
    public void   setTitleExtra(String t)       { this.titleExtra = t; }

    public String getSrc()                      { return src; }
    public void   setSrc(String src)            { this.src = src; }
}
