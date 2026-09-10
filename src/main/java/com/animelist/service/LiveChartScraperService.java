package com.animelist.service;

import com.animelist.model.LiveChartResult;
import com.animelist.model.Season;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scrape les résultats de recherche LiveChart via Playwright (headless Chromium).
 *
 * Première utilisation : Playwright télécharge automatiquement les navigateurs
 * dans ~/.cache/ms-playwright — lancez une fois `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"`.
 */
@Service
public class LiveChartScraperService {

    /**
     * Recherche des séries sur LiveChart et retourne les résultats.
     *
     * @param query texte de recherche
     * @return liste de résultats (id, nom, titleExtra, src)
     */
    public List<LiveChartResult> search(String query) {
        // Playwright gère lui-même le cycle de vie du browser
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );
            Page page = browser.newPage();

            String url = "https://www.livechart.me/search?q=" + query.replace(" ", "+");
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

            // Même sélecteur que ton code JS original
            List<LiveChartResult> results = page.locator("li.anime-item").all()
                .stream()
                .map(el -> {
                    String id         = el.getAttribute("data-anime-id");
                    String nom        = el.getAttribute("data-title");
                    String src        = el.locator("img").count() > 0
                                            ? el.locator("img").first().getAttribute("src")
                                            : null;
                    String titleExtra = el.locator(".title-extra").count() > 0
                                            ? el.locator(".title-extra").first().innerText().trim()
                                            : null;
                    return new LiveChartResult(id, nom, titleExtra, src);
                })
                .toList();

            browser.close();
            return results;
        }
    }


    /**
     * Recupere les informations utiles a l'identification de la serie sur Kitsun et LiveCart.
     *
     * @param liveChartAnimeId ID d'un element de la serie
     * @return SeasonIdentificator composé de l'ID de saison et de franchise liveChart ainsi que l'ID de saison Kitsu
     */
    public List<Season> getSeasons(String liveChartAnimeId) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );
            Page page = browser.newPage();

            page.navigate(
                "https://www.livechart.me/anime/" + liveChartAnimeId,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE)
            );

            // ── CORRECTION : compter d'abord, sans attente bloquante ──
            Locator franchiseLinks = page.locator("a[href*='/franchises/']");
            
            if (franchiseLinks.count() == 0) {
                // Pas de franchise → série standalone, on scrape juste cette page
                List<Season> result = List.of(buildFromPage(page, liveChartAnimeId, null));
                browser.close();
                return result;
            }

            // ── Franchise trouvée ──────────────────────────────────────
            String franchiseHref = franchiseLinks.first().getAttribute("href");
            String franchiseId   = franchiseHref.replaceAll(".*/franchises/(\\d+).*", "$1");
            String franchiseUrl  = "https://www.livechart.me" + franchiseHref;

            System.out.println("Franchise ID: " + franchiseId + " → " + franchiseUrl);

            page.navigate(franchiseUrl,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

            List<Season> seasons = page.locator("article[data-anime-id]").all()
                .stream()
                .map(el -> {
                    String seasonId    = el.getAttribute("data-anime-id");
                    String romaji      = el.getAttribute("data-romaji");
                    String english     = el.getAttribute("data-english");
                    String kitsuId     = extractId(el, "a[href*='kitsu.app/anime/']", "href");
                    String anisearchId = extractId(el, "a[href*='anisearch.com/anime/']", "href");

                    String dateRaw = extractText(el, ".lc-anime-card--date");
                    String dateStr = dateRaw;
                    String timeStr = null;
                    if (dateRaw != null && dateRaw.contains(" at ")) {
                        int idx = dateRaw.indexOf(" at ");
                        dateStr = dateRaw.substring(0, idx).trim();
                        timeStr = dateRaw.substring(idx + 4).trim();
                    }

                    String studio = extractText(el, ".lc-anime-card--studios a");

                    List<Locator> metaItems = el.locator(".lc-anime-card--metadata .flex-1").all();
                    String metaEps = null, metaDuree = null;
                    if (metaItems.size() >= 2) {
                        String[] parts = metaItems.get(1).innerText().trim().split("×");
                        if (parts.length == 2) {
                            metaEps   = parts[0].trim().replaceAll("[^0-9]", "");
                            metaDuree = parts[1].trim().replaceAll("[^0-9]", "");
                        }
                    }
                    String format = extractText(el, ".lc-anime-card--release-state > div:first-child");
                    if (format != null) {
                        format = format.split(" ")[0];
                    }

                    Season season = new Season(
                        franchiseId, seasonId, kitsuId, anisearchId, romaji,
                        english, dateStr, studio, metaEps, metaDuree, format
                    );
                    // Le même champ date LiveChart affiche soit la date de première
                    // (série pas encore diffusée), soit le prochain épisode (série en
                    // cours) — on le recopie donc aussi comme "prochain épisode".
                    season.setJourNextEp(season.getJourPremiere());
                    season.setMoisNextEp(season.getMoisPremiere());
                    season.setAnneeNextEp(season.getAnneePremiere());
                    season.setHeureNextEp(timeStr);
                    return season;
                })
                .toList();

            browser.close();
            return seasons;
        }
    }

    // private Season buildFromSeasonPage(Page page, String liveChartAnimeId, String franchiseId) {
    //     String kitsunId = extractId(page.locator("a[href*='kitsu.app/anime/']").first(), "href");
    //     String anisearchId = extractId(page.locator("a[href*='anisearch.com/anime/']").first(), "href");
    //     return new Season(franchiseId, liveChartAnimeId, kitsunId, anisearchId);
    // }

    private Season buildFromPage(Page page, String liveChartAnimeId, String franchiseId) {
        String romaji      = extractAttr(page.locator("li[data-anime-id]").first(), "data-romaji");
        String english     = extractAttr(page.locator("li[data-anime-id]").first(), "data-english");
        String kitsuId     = extractId(page.locator("a[href*='kitsu.app/anime/']").first(), "href");
        String anisearchId = extractId(page.locator("a[href*='anisearch.com/anime/']").first(), "href");

        String dateRaw = extractText(page.locator(".lc-anime-card--date").first());
        String dateStr = dateRaw;
        String timeStr = null;
        if (dateRaw != null && dateRaw.contains(" at ")) {
            int idx = dateRaw.indexOf(" at ");
            dateStr = dateRaw.substring(0, idx).trim();
            timeStr = dateRaw.substring(idx + 4).trim();
        }

        String studio = extractText(page.locator(".lc-anime-card--studios a").first());

        List<Locator> metaItems = page.locator(".lc-anime-card--metadata .flex-1").all();
        String metaEps = null, metaDuree = null;
        if (metaItems.size() >= 2) {
            String[] parts = metaItems.get(1).innerText().trim().split("×");
            if (parts.length == 2) {
                metaEps   = parts[0].trim().replaceAll("[^0-9]", "");
                metaDuree = parts[1].trim().replaceAll("[^0-9]", "");
            }
        }

        String format = extractText(page.locator("a[href*='/schedules/']"));

        Season season = new Season(
            franchiseId, liveChartAnimeId, kitsuId, anisearchId, romaji,
            english, dateStr, studio, metaEps, metaDuree, format
        );
        season.setJourNextEp(season.getJourPremiere());
        season.setMoisNextEp(season.getMoisPremiere());
        season.setAnneeNextEp(season.getAnneePremiere());
        season.setHeureNextEp(timeStr);
        return season;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────
    private String extractId(Locator parent, String selector, String attr) {
        String url = extractAttr(parent.locator(selector).first(), attr);
        if (url == null) return null;
        String[] parts = url.split("/");
        return parts[parts.length - 1].replaceAll("[^0-9]", "");
    }

    private String extractId(Locator locator, String attr) {
        String url = extractAttr(locator, attr);
        if (url == null) return null;
        String[] parts = url.split("/");
        return parts[parts.length - 1].replaceAll("[^0-9]", "");
    }

    private String extractAttr(Locator locator, String attr) {
        return locator.count() > 0 ? locator.getAttribute(attr) : null;
    }

    private String extractText(Locator locator) {
        return locator.count() > 0 ? locator.innerText().trim() : null;
    }

    private String extractText(Locator parent, String selector) {
        return extractText(parent.locator(selector).first());
    }
}