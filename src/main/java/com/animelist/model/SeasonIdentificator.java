package com.animelist.model;

public class SeasonIdentificator {

    private String liveChartId;
    private String franchiseId;
    private String kitsuId;
    private String kitsuUrl;
    private String aniSearchId;
    private String aniSearchUrl;

    public SeasonIdentificator() {}

    public SeasonIdentificator(String liveChartId, String franchiseId, String kitsuId, String kitsuUrl, String aniSearchId, String aniSearchUrl) {
        this.liveChartId    = liveChartId;
        this.franchiseId    = franchiseId;
        this.kitsuId        = kitsuId;
        this.kitsuUrl       = kitsuUrl;
        this.aniSearchId    = aniSearchId;
        this.aniSearchUrl   = aniSearchUrl;
    }

    public String getLiveChartId()                      { return liveChartId; }
    public void   setLiveChartId(String liveChartId)    { this.liveChartId = liveChartId; }

    public String getFranchiseId()                      { return franchiseId; }
    public void   setFranchiseId(String franchiseId)    { this.franchiseId = franchiseId; }

    public String getKitsuId()                          { return kitsuId; }
    public void   setKitsuId(String kitsuId)            { this.kitsuId = kitsuId; }

    public String getKitsuUrl()                         { return kitsuUrl; }
    public void   setKitsuUrl(String kitsuUrl)          { this.kitsuUrl = kitsuUrl; }

    public String getAniSearchId()                      { return aniSearchId; }
    public void   setAniSearchId(String aniSearchId)    { this.aniSearchId = aniSearchId; }

    public String getAniSearchUrl()                     { return aniSearchUrl; }
    public void   setAniSearchUrl(String aniSearchUrl)  { this.aniSearchUrl = aniSearchUrl; }
}
