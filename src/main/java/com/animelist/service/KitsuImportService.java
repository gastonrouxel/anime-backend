package com.animelist.service;

import com.animelist.model.*;
import com.animelist.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KitsuImportService {

    @PersistenceContext
    private EntityManager em;

    private static final String BASE    = "https://kitsu.app/api/edge";
    private static final String INCLUDE = "mediaRelationships.destination,categories,animeProductions.producer,episodes";

    // Sous-types Kitsu à ne jamais importer comme saison/hors-série (ex: clips
    // musicaux découverts via les mediaRelationships d'une franchise).
    private static final Set<String> EXCLUDED_SUBTYPES = Set.of("music");

    private static final HttpClient  HTTP = HttpClient.newBuilder()
                                                      .followRedirects(HttpClient.Redirect.NORMAL)
                                                      .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SerieRepository  serieRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;
    private final GenreRepository  genreRepository;
    private final StudioRepository studioRepository;
    private final LiveChartScraperService liveChartScraper;

    public KitsuImportService(SerieRepository serieRepository,
                               SeasonRepository seasonRepository,
                               EpisodeRepository episodeRepository,
                               GenreRepository genreRepository,
                               StudioRepository studioRepository,
                               LiveChartScraperService liveChartScraper) {
        this.serieRepository   = serieRepository;
        this.seasonRepository  = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.genreRepository   = genreRepository;
        this.studioRepository  = studioRepository;
        this.liveChartScraper  = liveChartScraper;
    }

    // ── Recherche ────────────────────────────────────────────────────────────
    /**
     * Recherche des animes directement sur l'API Kitsu (utile pour les animes
     * occidentaux absents de LiveChart).
     *
     * @param query texte de recherche
     * @return liste de résultats (id Kitsu, nom, titre original, poster)
     */
    public List<LiveChartResult> search(String query) {
        List<LiveChartResult> results = new ArrayList<>();
        String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String request = BASE + "/anime?filter[text]=" + encoded + "&page[limit]=20&page[offset]=0";

        JsonNode root;
        try {
            root = get(request);
        } catch (Exception e) {
            System.err.println("appel API incorrecte :" + request);
            return results;
        }

        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return results;
        }

        for (JsonNode anime : data) {
            String id = anime.path("id").asText(null);
            JsonNode attributes = anime.path("attributes");
            String nom = attributes.path("canonicalTitle").asText(null);

            JsonNode titles = attributes.path("titles");
            String titleExtra = titles.hasNonNull("ja_jp")
                    ? titles.path("ja_jp").asText(null)
                    : titles.path("en_jp").asText(null);

            JsonNode poster = attributes.path("posterImage");
            String src = poster.hasNonNull("large")
                    ? poster.path("large").asText(null)
                    : poster.hasNonNull("medium")
                    ? poster.path("medium").asText(null)
                    : poster.path("original").asText(null);

            results.add(new LiveChartResult(id, nom, titleExtra, src));
        }

        return results;
    }

    // ── Point d'entrée ────────────────────────────────────────────────────────
    /**
     * @param lcSeasons liste retournée par LiveChartScraperService.getSeasons()
     *                  Toutes les saisons partagent le même franchiseId.
     *                  La première est considérée comme appartenant à la saga principale.
     */
    public void importSerie(List<Season> lcSeasons) throws Exception {
        importSerie(lcSeasons, false);
    }

    @Transactional
    public void importSerie(List<Season> lcSeasons, boolean toWatch) throws Exception {

        if (lcSeasons == null || lcSeasons.isEmpty())
            throw new IllegalArgumentException("Liste de saisons vide");

        // ── 1. Identifiants de référence ──────────────────────────────────────────
        System.out.println("── 1. Identifiants de référence ──────────────────────────────────────────");

        Season lcFirst = lcSeasons.get(0);

        for (Season season : lcSeasons) {
            String format = season.getFormat();

            if (format != null && ("movie".equalsIgnoreCase(format) || "streaming".equalsIgnoreCase(format))) {
                lcFirst = season;
            }
            if (format != null && "tv".equalsIgnoreCase(format)) {
                lcFirst = season;
                break;
            }
            
        }

        String startKitsuId = String.valueOf(lcFirst.getIdKitsu());
        Integer idFranchise = lcFirst.getIdFranchise();
        Integer idFirstLc   = lcFirst.getIdLivechart();

        // ── 2. Traversal Kitsu : trouve la première saison de la saga ─────────────
        System.out.println("── 2. Traversal Kitsu : trouve la première saison de la saga ─────────────");

        System.out.println("\nRecherche du début de la série depuis Kitsu ID " + startKitsuId + "...");
        String firstKitsuId = findFirst(startKitsuId);
        System.out.println("  ✓ Première saison Kitsu : " + firstKitsuId);

        // ── 3. Construction de la liste ordonnée (prequel → sequel) ───────────────
        System.out.println("── 3. Construction de la liste ordonnée (prequel → sequel) ───────────────");

        System.out.println("\nConstruction de la liste ordonnée...");
        List<String> orderedKitsuIds = buildOrderedList(firstKitsuId);
        System.out.println("  ✓ " + orderedKitsuIds.size() + " saison(s) trouvée(s) via Kitsu");

        // ── 4. Index LiveChart par idKitsu ────────────────────────────────────────
        System.out.println("── 4. Index LiveChart par idKitsu ────────────────────────────────────────");

        Map<Integer, Season> lcByKitsuId        = new HashMap<>();
        Map<String, Season>  lcByKitsuNameFr    = new HashMap<>();
        Map<String, Season>  lcByKitsuNameOrig  = new HashMap<>();
        for (Season s : lcSeasons) {
            if (s.getIdKitsu() != null)
                lcByKitsuId.put(s.getIdKitsu(), s);
            if (s.getNomFr() != null)
                lcByKitsuNameFr.put(normalize(s.getNomFr()), s);
            if (s.getNomOrig() != null)
                lcByKitsuNameOrig.put(normalize(s.getNomOrig()), s);
        }

        // Ensemble des kitsuIds traités pendant le parcours saga principale
        Set<Integer> processedKitsuIds = new HashSet<>();

        // ── 5. Import saga principale ─────────────────────────────────────────────
        System.out.println("── 5. Import saga principale ─────────────────────────────────────────────");

        List<Season> seasons = new ArrayList<>();
        // Candidats hors-série découverts via les mediaRelationships Kitsu
        // (utile notamment quand l'import ne vient pas de LiveChart : on n'a
        // alors aucune autre source pour connaître les à-côtés de la saga).
        Set<String> kitsuHorsSerieIds = new HashSet<>();

        for (int i = 0; i < orderedKitsuIds.size(); i++) {
            String kitsuId = orderedKitsuIds.get(i);
            System.out.println("\n[" + (i + 1) + "/" + orderedKitsuIds.size() + "] Kitsu ID " + kitsuId);

            JsonNode root     = get(BASE + "/anime/" + kitsuId + "?include=" + INCLUDE);
            JsonNode included = root.path("included");
            JsonNode data     = root.get("data");

            Season season = buildSeason(kitsuId, data, i);

            season.setSagaPrincipale(true);

            Season lcMatch = lcByKitsuId.get(Integer.parseInt(kitsuId));
            if (lcMatch == null) lcMatch = lcByKitsuNameFr.get(normalize(season.getNomFr()));
            if (lcMatch == null) lcMatch = lcByKitsuNameOrig.get(normalize(season.getNomOrig()));

            if (lcMatch != null) {
                season.setIdLivechart(lcMatch.getIdLivechart());
                season.setIdAnisearch(lcMatch.getIdAnisearch());
            }

            processedKitsuIds.add(Integer.parseInt(kitsuId));
            kitsuHorsSerieIds.addAll(findOtherAnimeRelations(included));

            enrichStudios(season, included);
            enrichGenres(season, data, included);
            enrichEpisodes(season, included);
            applyEpisodeStats(season);

            seasons.add(season);
            logSeason(season, i + 1, orderedKitsuIds.size());
        }

        Map<Integer, Season> lcByLivechartId    = new HashMap<>();
        for (Season s : seasons) {
            if (s.getIdLivechart() != null)
                lcByLivechartId.put(s.getIdLivechart(), s);
        }

        // ── 6. Hors-séries : entrées non traitées (LiveChart + Kitsu) ─────────────
        System.out.println("── 6. Hors-séries : entrées non traitées (LiveChart + Kitsu) ─────────────");

        int hsIndex = orderedKitsuIds.size(); // watchIndex continue après la saga
        List<Season> extraLcSeasons = lcSeasons.stream()
                .filter(s -> s.getIdKitsu() == null || !processedKitsuIds.contains(s.getIdKitsu()))
                .toList();

        // IDs Kitsu déjà couverts par la saga principale ou par les entrées LiveChart :
        // on les retire des candidats auto-découverts pour ne pas les traiter deux fois.
        Set<String> alreadyHandled = new HashSet<>();
        processedKitsuIds.forEach(id -> alreadyHandled.add(String.valueOf(id)));
        extraLcSeasons.forEach(s -> { if (s.getIdKitsu() != null) alreadyHandled.add(String.valueOf(s.getIdKitsu())); });
        kitsuHorsSerieIds.removeAll(alreadyHandled);

        System.out.println("\n" + extraLcSeasons.size() + " hors-série(s) via LiveChart, "
                + kitsuHorsSerieIds.size() + " hors-série(s) découverte(s) via Kitsu à importer...");

        for (Season lcExtra : extraLcSeasons) {
            String kitsuId = lcExtra.getIdKitsu() != null
                    ? String.valueOf(lcExtra.getIdKitsu())
                    : null;

            Season season;
            if (kitsuId != null) {
                System.out.println("\n[HS] Kitsu ID " + kitsuId);
                JsonNode root     = get(BASE + "/anime/" + kitsuId + "?include=" + INCLUDE);
                JsonNode included = root.path("included");
                JsonNode data     = root.get("data");

                if (isExcludedSubtype(data)) {
                    System.out.println("  ✗ Ignoré (subtype exclu : "
                            + data.path("attributes").path("subtype").asText() + ")");
                    continue;
                }

                season = buildSeason(kitsuId, data, hsIndex);

                enrichStudios(season, included);
                enrichGenres(season, data, included);
                enrichEpisodes(season, included);
                applyEpisodeStats(season);
            } else {
                // Pas d'ID Kitsu : on construit un Season minimal depuis LC
                System.out.println("\n[HS] Pas d'ID Kitsu — données LC uniquement");
                season = new Season();
                season.setWatchIndex(hsIndex);
                season.setNomFr(lcExtra.getNomFr() != null ? lcExtra.getNomFr() : lcExtra.getNomOrig());
                season.setNomOrig(lcExtra.getNomOrig());
                season.setAnneePremiere(lcExtra.getAnneePremiere());
                season.setMoisPremiere(lcExtra.getMoisPremiere());
                season.setJourPremiere(lcExtra.getJourPremiere());
                season.setNbEpisodes(lcExtra.getNbEpisodes());
                season.setDureeMoyenne(lcExtra.getDureeMoyenne());
                if (season.getNbEpisodes() != null && season.getDureeMoyenne() != null)
                    season.setDureeTotale(season.getNbEpisodes() * season.getDureeMoyenne());
            }

            season.setSagaPrincipale(false);
            season.setIdLivechart(lcExtra.getIdLivechart());
            season.setIdAnisearch(lcExtra.getIdAnisearch());
            if (lcExtra.getIdKitsu() != null)
                season.setIdKitsu(lcExtra.getIdKitsu());

            Season lcMatch = lcByLivechartId.get(season.getIdLivechart());
            if(lcMatch != null) {
                System.out.println("-> season deja presente : " + lcMatch.getIdLivechart() + " - " + lcMatch.getNomFr());
                continue;
            }

            seasons.add(season);
            hsIndex++;
            logSeason(season, hsIndex, -1);
        }

        // Hors-séries découvertes uniquement via les relations Kitsu (pas de page
        // LiveChart correspondante — cas typique d'un import Kitsu seul).
        for (String kitsuId : kitsuHorsSerieIds) {
            System.out.println("\n[HS Kitsu] Kitsu ID " + kitsuId);
            Season season;
            try {
                JsonNode root     = get(BASE + "/anime/" + kitsuId + "?include=" + INCLUDE);
                JsonNode included = root.path("included");
                JsonNode data     = root.get("data");

                if (isExcludedSubtype(data)) {
                    System.out.println("  ✗ Ignoré (subtype exclu : "
                            + data.path("attributes").path("subtype").asText() + ")");
                    continue;
                }

                season = buildSeason(kitsuId, data, hsIndex);

                enrichStudios(season, included);
                enrichGenres(season, data, included);
                enrichEpisodes(season, included);
                applyEpisodeStats(season);
            } catch (Exception e) {
                System.out.println("  ✗ Impossible de récupérer l'anime " + kitsuId + " : " + e.getMessage());
                continue;
            }

            season.setSagaPrincipale(false);

            Season lcMatch = lcByKitsuId.get(Integer.parseInt(kitsuId));
            if (lcMatch != null) {
                season.setIdLivechart(lcMatch.getIdLivechart());
                season.setIdAnisearch(lcMatch.getIdAnisearch());
            }

            seasons.add(season);
            hsIndex++;
            logSeason(season, hsIndex, -1);
        }

        // ── 7. Construction et sauvegarde de la Serie ─────────────────────────────
        System.out.println("── 7. Construction et sauvegarde de la Serie ─────────────────────────────");
        Season first = seasons.get(0);

        Serie serie = new Serie();
        serie.setIdFranchise(idFranchise);
        serie.setIdFirstEltKitsu(Integer.parseInt(firstKitsuId));
        serie.setIdFirstEltLiveChart(idFirstLc);
        if (lcFirst.getIdAnisearch() != null)
            serie.setIdFirstEltAnisearch(lcFirst.getIdAnisearch());
        serie.setNomFr(first.getNomFr() != null ? first.getNomFr() : first.getNomOrig());
        serie.setNomOrig(first.getNomOrig() != null ? first.getNomOrig() : first.getNomFr());
        serie.setImage(first.getImageUrl());
        serie.setImageBaniere(first.getImageBaniereUrl());
        serie.setToWatch(toWatch);
        serie = serieRepository.save(serie);

        // ── 8. Sauvegarde des saisons (genres + épisodes extraits, jamais remis dans la collection)
        Map<Season, List<SeasonGenre>> genresBySeason   = new LinkedHashMap<>();
        Map<Season, List<Episode>>     episodesBySeason = new LinkedHashMap<>();

        Integer idSerie    = serie.getId();
        Integer nbEpisodes = 0;

        for (Season s : seasons) {
            s.setIdSerie(idSerie);

            // Extraire genres et épisodes SANS les remettre dans la collection managed
            List<SeasonGenre> genres   = new ArrayList<>(s.getSeasonGenres());
            List<Episode>     episodes = new ArrayList<>(s.getEpisodes());

            System.out.println("saison : " + s.getNomFr() + " -> " + episodes.size() + " episodes");
            nbEpisodes += episodes.size();

            s.getSeasonGenres().clear();
            s.getEpisodes().clear();
            seasonRepository.save(s);
            // NE PAS remettre les genres dans la collection — on les insère via native query

            genresBySeason.put(s, genres);
            episodesBySeason.put(s, episodes);
        }

        // ── 9. INSERT genres via native query (bypass total du cache Hibernate)
        for (Season s : seasons) {
            List<SeasonGenre> genres = genresBySeason.get(s);
            if (genres != null) {
                for (SeasonGenre sg : genres) {
                    Integer idGenre = sg.getGenre().getId();
                    if (idGenre == null) continue;
                    em.createNativeQuery(
                        "INSERT IGNORE INTO season_genre (id_season, id_genre, is_main_genre) VALUES (?, ?, ?)")
                      .setParameter(1, s.getId())
                      .setParameter(2, idGenre)
                      .setParameter(3, sg.getIsMainGenre() ? 1 : 0)
                      .executeUpdate();
                }
            }

            List<Episode> episodes = episodesBySeason.get(s);
            if (episodes != null && !episodes.isEmpty()) {
                for (Episode ep : episodes) {
                    ep.setSeason(s);
                }
                episodeRepository.saveAll(episodes);
            }
        }

        System.out.println("\n✅ " + seasons.size() + " saison(s) importée(s) pour la série " + idSerie
                + " (" + orderedKitsuIds.size() + " principale(s), "
                + extraLcSeasons.size() + " hors-série(s))");
        System.out.println(nbEpisodes + " episodes");
    }

    // ── Mise à jour d'une série déjà importée ─────────────────────────────────
    /**
     * Ré-importe une série sans écraser les données déjà stockées :
     *  - la Serie elle-même n'est jamais modifiée (à part updated_at) ;
     *  - les champs factuels des saisons déjà connues (statut, note, date de
     *    première, prochain épisode) sont rafraîchis ; titre/image/format ne
     *    sont remplis que s'ils étaient vides (pas d'écrasement d'une
     *    correction manuelle) ; note perso jamais touchée ;
     *  - genres et studios : uniquement ajoutés si la saison n'en avait aucun ;
     *  - épisodes : fusion (video_link / temps_viso préservés, nouveaux
     *    épisodes ajoutés) ;
     *  - la chaîne prequel/sequel et les relations Kitsu sont re-parcourues,
     *    donc de nouvelles saisons / hors-séries peuvent apparaître ;
     *  - prochain épisode : LiveChart en priorité, repli sur Kitsu (nextRelease).
     */
    @Transactional
    public void updateSerie(Integer serieId) throws Exception {
        Serie serie = serieRepository.findById(serieId)
                .orElseThrow(() -> new EntityNotFoundException("Série " + serieId + " introuvable"));

        List<Season> existing = seasonRepository.findByIdSerie(serieId);
        if (existing.isEmpty())
            throw new IllegalStateException("Aucune saison trouvée pour la série " + serieId);
        existing.sort(Comparator.comparing(s -> s.getWatchIndex() == null ? 0 : s.getWatchIndex()));

        Season firstMain = existing.stream()
                .filter(Season::isSagaPrincipale)
                .min(Comparator.comparing(s -> s.getWatchIndex() == null ? 0 : s.getWatchIndex()))
                .orElse(null);
        if (firstMain == null || firstMain.getIdKitsu() == null)
            throw new IllegalStateException(
                "Mise à jour impossible : aucune saison principale avec un ID Kitsu pour la série " + serieId);

        System.out.println("═══ MISE À JOUR — Série " + serieId + " (" + serie.getNomFr() + ") ═══");

        // ── Rafraîchissement LiveChart (si une saison connaît son ID LiveChart) ──
        List<Season> lcSeasons = new ArrayList<>();
        Season lcAnchor = existing.stream().filter(s -> s.getIdLivechart() != null).findFirst().orElse(null);
        if (lcAnchor != null) {
            try {
                lcSeasons = liveChartScraper.getSeasons(String.valueOf(lcAnchor.getIdLivechart()));
            } catch (Exception e) {
                System.err.println("⚠ Rafraîchissement LiveChart échoué : " + e.getMessage());
            }
        }
        Map<Integer, Season> lcByKitsuId = lcSeasons.stream()
                .filter(s -> s.getIdKitsu() != null)
                .collect(Collectors.toMap(Season::getIdKitsu, s -> s, (a, b) -> a));

        Map<Integer, Season> existingByKitsuId = existing.stream()
                .filter(s -> s.getIdKitsu() != null)
                .collect(Collectors.toMap(Season::getIdKitsu, s -> s, (a, b) -> a));
        Map<Integer, Season> existingByLivechartId = existing.stream()
                .filter(s -> s.getIdLivechart() != null)
                .collect(Collectors.toMap(Season::getIdLivechart, s -> s, (a, b) -> a));

        // ── Re-parcourt la chaîne prequel/sequel : peut révéler de nouvelles saisons ──
        String startId = findFirst(String.valueOf(firstMain.getIdKitsu()));
        List<String> orderedKitsuIds = buildOrderedList(startId);

        Set<Integer> processedKitsuIds = new HashSet<>();
        Set<String>  kitsuHorsSerieIds = new HashSet<>();
        int updatedCount = 0, newCount = 0;

        int watchIndex = 0;
        for (String kitsuId : orderedKitsuIds) {
            System.out.println("\n[" + (watchIndex + 1) + "/" + orderedKitsuIds.size() + "] Kitsu ID " + kitsuId);
            JsonNode root     = get(BASE + "/anime/" + kitsuId + "?include=" + INCLUDE);
            JsonNode included = root.path("included");
            JsonNode data     = root.get("data");

            if (isExcludedSubtype(data)) { watchIndex++; continue; }

            Integer kitsuIdInt = Integer.parseInt(kitsuId);
            processedKitsuIds.add(kitsuIdInt);
            kitsuHorsSerieIds.addAll(findOtherAnimeRelations(included));

            Season target  = existingByKitsuId.get(kitsuIdInt);
            boolean isNew  = target == null;
            upsertSeason(target, isNew, kitsuId, data, included, watchIndex, serieId, true,
                    lcByKitsuId.get(kitsuIdInt));
            if (isNew) newCount++; else updatedCount++;

            watchIndex++;
        }

        // ── Hors-séries : entrées LiveChart non traitées + découvertes Kitsu ────
        int hsIndex = watchIndex;
        List<Season> extraLcSeasons = lcSeasons.stream()
                .filter(s -> s.getIdKitsu() == null || !processedKitsuIds.contains(s.getIdKitsu()))
                .toList();

        Set<String> alreadyHandled = new HashSet<>();
        processedKitsuIds.forEach(id -> alreadyHandled.add(String.valueOf(id)));
        extraLcSeasons.forEach(s -> { if (s.getIdKitsu() != null) alreadyHandled.add(String.valueOf(s.getIdKitsu())); });
        kitsuHorsSerieIds.removeAll(alreadyHandled);

        for (Season lcExtra : extraLcSeasons) {
            if (lcExtra.getIdKitsu() != null) {
                String kitsuId = String.valueOf(lcExtra.getIdKitsu());
                JsonNode root     = get(BASE + "/anime/" + kitsuId + "?include=" + INCLUDE);
                JsonNode included = root.path("included");
                JsonNode data     = root.get("data");
                if (isExcludedSubtype(data)) continue;

                Season target = existingByKitsuId.get(lcExtra.getIdKitsu());
                boolean isNew = target == null;
                upsertSeason(target, isNew, kitsuId, data, included, hsIndex, serieId, false, lcExtra);
                if (isNew) newCount++; else updatedCount++;
            } else {
                // Pas d'ID Kitsu : entrée LiveChart pure, données limitées.
                Season target = existingByLivechartId.get(lcExtra.getIdLivechart());
                if (target == null) {
                    target = new Season();
                    target.setIdSerie(serieId);
                    target.setIdLivechart(lcExtra.getIdLivechart());
                    target.setIdAnisearch(lcExtra.getIdAnisearch());
                    target.setSagaPrincipale(false);
                    target.setWatchIndex(hsIndex);
                    target.setNomFr(lcExtra.getNomFr() != null ? lcExtra.getNomFr() : lcExtra.getNomOrig());
                    target.setNomOrig(lcExtra.getNomOrig());
                    target.setNbEpisodes(lcExtra.getNbEpisodes());
                    target.setDureeMoyenne(lcExtra.getDureeMoyenne());
                    if (target.getNbEpisodes() != null && target.getDureeMoyenne() != null)
                        target.setDureeTotale(target.getNbEpisodes() * target.getDureeMoyenne());
                    newCount++;
                } else {
                    updatedCount++;
                }
                // Champs factuels toujours rafraîchis
                target.setJourPremiere(lcExtra.getJourPremiere());
                target.setMoisPremiere(lcExtra.getMoisPremiere());
                target.setAnneePremiere(lcExtra.getAnneePremiere());
                target.setJourNextEp(lcExtra.getJourNextEp());
                target.setMoisNextEp(lcExtra.getMoisNextEp());
                target.setAnneeNextEp(lcExtra.getAnneeNextEp());
                target.setHeureNextEp(lcExtra.getHeureNextEp());
                seasonRepository.save(target);
            }
            hsIndex++;
        }

        for (String kitsuId : kitsuHorsSerieIds) {
            JsonNode root, data, included;
            try {
                root     = get(BASE + "/anime/" + kitsuId + "?include=" + INCLUDE);
                included = root.path("included");
                data     = root.get("data");
            } catch (Exception e) {
                System.out.println("  ✗ Impossible de récupérer l'anime " + kitsuId + " : " + e.getMessage());
                continue;
            }
            if (isExcludedSubtype(data)) continue;

            Integer kitsuIdInt = Integer.parseInt(kitsuId);
            Season target = existingByKitsuId.get(kitsuIdInt);
            boolean isNew = target == null;
            upsertSeason(target, isNew, kitsuId, data, included, hsIndex, serieId, false, lcByKitsuId.get(kitsuIdInt));
            if (isNew) newCount++; else updatedCount++;
            hsIndex++;
        }

        // ── La Serie elle-même n'est jamais modifiée, à part updated_at ─────────
        // (updated_at est en lecture seule côté JPA — on force le "touch" en SQL direct)
        em.createNativeQuery("UPDATE series SET updated_at = NOW() WHERE id_serie = ?1")
          .setParameter(1, serieId)
          .executeUpdate();

        System.out.println("\n✅ Mise à jour terminée pour la série " + serieId
                + " — " + updatedCount + " saison(s) rafraîchie(s), " + newCount + " nouvelle(s)");
    }

    /**
     * Insère ou met à jour une saison à partir des données Kitsu fraîchement
     * récupérées. Voir la politique détaillée en tête de updateSerie().
     */
    private Season upsertSeason(Season target, boolean isNew, String kitsuId, JsonNode data, JsonNode included,
                                 int watchIndex, Integer idSerie, boolean sagaPrincipale, Season lcMatch) {
        if (isNew) {
            target = buildSeason(kitsuId, data, watchIndex);
            target.setIdSerie(idSerie);
            System.out.println("  ＋ Nouvelle saison : " + target.getNomFr());
        } else {
            Season fresh = buildSeason(kitsuId, data, watchIndex);
            mergeSeasonFields(target, fresh);
            target.setWatchIndex(watchIndex);
        }
        target.setSagaPrincipale(sagaPrincipale);

        if (lcMatch != null) {
            if (target.getIdLivechart() == null) target.setIdLivechart(lcMatch.getIdLivechart());
            if (target.getIdAnisearch() == null) target.setIdAnisearch(lcMatch.getIdAnisearch());
        }
        applyNextEpisode(target, data, lcMatch);

        if (target.getStudio() == null || target.getStudio().isEmpty())
            enrichStudios(target, included);

        boolean fillGenres = target.getSeasonGenres() == null || target.getSeasonGenres().isEmpty();
        if (fillGenres) enrichGenres(target, data, included);
        List<SeasonGenre> freshGenres = fillGenres ? new ArrayList<>(target.getSeasonGenres()) : List.of();
        target.getSeasonGenres().clear(); // toujours : les genres s'insèrent via requête native, jamais par cascade

        List<Episode> newEpisodes = List.of();
        if (isNew) {
            enrichEpisodes(target, included);
            newEpisodes = new ArrayList<>(target.getEpisodes());
            target.getEpisodes().clear();
        }

        target = seasonRepository.save(target);

        if (!freshGenres.isEmpty()) {
            for (SeasonGenre sg : freshGenres) {
                Integer idGenre = sg.getGenre().getId();
                if (idGenre == null) continue;
                em.createNativeQuery(
                    "INSERT IGNORE INTO season_genre (id_season, id_genre, is_main_genre) VALUES (?, ?, ?)")
                  .setParameter(1, target.getId())
                  .setParameter(2, idGenre)
                  .setParameter(3, sg.getIsMainGenre() ? 1 : 0)
                  .executeUpdate();
            }
        }

        if (isNew) {
            if (!newEpisodes.isEmpty()) {
                for (Episode ep : newEpisodes) ep.setSeason(target);
                episodeRepository.saveAll(newEpisodes);
            }
            applyEpisodeStats(target, newEpisodes);
        } else {
            List<Episode> merged = mergeEpisodesInPlace(target, included);
            applyEpisodeStats(target, merged);
        }

        return seasonRepository.save(target);
    }

    /**
     * Applique la politique de rafraîchissement d'une saison déjà en base :
     *  - statut, note, date de première : toujours rafraîchis (faits externes) ;
     *  - titre, images, format : uniquement si absents (pas d'écrasement d'une
     *    correction manuelle) ;
     *  - note perso : jamais touchée.
     */
    private void mergeSeasonFields(Season target, Season fresh) {
        target.setStatus(fresh.getStatus());
        target.setNote(fresh.getNote());
        target.setJourPremiere(fresh.getJourPremiere());
        target.setMoisPremiere(fresh.getMoisPremiere());
        target.setAnneePremiere(fresh.getAnneePremiere());

        if (target.getNomFr() == null)           target.setNomFr(fresh.getNomFr());
        if (target.getNomOrig() == null)         target.setNomOrig(fresh.getNomOrig());
        if (target.getImageUrl() == null)        target.setImageUrl(fresh.getImageUrl());
        if (target.getImageBaniereUrl() == null) target.setImageBaniereUrl(fresh.getImageBaniereUrl());
        if (target.getFormat() == null)          target.setFormat(fresh.getFormat());
        // note_perso, id_kitsu/id_livechart/id_anisearch existants, genres, studios,
        // épisodes (video_link / temps_viso) : jamais écrasés ici.
    }

    /**
     * Renseigne la date/heure du prochain épisode : LiveChart en priorité
     * (lcMatch), repli sur l'attribut "nextRelease" de Kitsu. Vide les champs
     * si la saison est terminée.
     */
    private void applyNextEpisode(Season target, JsonNode data, Season lcMatch) {
        if ("finished".equalsIgnoreCase(target.getStatus())) {
            target.setJourNextEp(null);
            target.setMoisNextEp(null);
            target.setAnneeNextEp(null);
            target.setHeureNextEp(null);
            return;
        }

        if (lcMatch != null && lcMatch.getJourNextEp() != null) {
            target.setJourNextEp(lcMatch.getJourNextEp());
            target.setMoisNextEp(lcMatch.getMoisNextEp());
            target.setAnneeNextEp(lcMatch.getAnneeNextEp());
            target.setHeureNextEp(lcMatch.getHeureNextEp());
            return;
        }

        String nextRelease = data.path("attributes").path("nextRelease").asText(null);
        if (nextRelease != null && !nextRelease.isBlank()) {
            try {
                java.time.OffsetDateTime dt = java.time.OffsetDateTime.parse(nextRelease);
                target.setJourNextEp((byte) dt.getDayOfMonth());
                target.setMoisNextEp((byte) dt.getMonthValue());
                target.setAnneeNextEp((short) dt.getYear());
                target.setHeureNextEp(String.format("%02d:%02d", dt.getHour(), dt.getMinute()));
            } catch (Exception ignored) {}
        }
    }

    // ── Traversal prequel / sequel ────────────────────────────────────────────
    // On réutilise le JSON déjà chargé (included contient les mediaRelationships)

    private String findFirst(String startId) throws Exception {
        String currentId = startId;
        Set<String> visited = new HashSet<>();
        while (true) {
            if (!visited.add(currentId))
                throw new IllegalStateException("Boucle détectée sur l'ID " + currentId);
            JsonNode root     = get(BASE + "/anime/" + currentId + "?include=" + INCLUDE);
            JsonNode included = root.path("included");
            String prequelId  = findNextInChain(included, "prequel", "sequel");
            if (prequelId == null) return currentId;
            System.out.println("  ← Remonte vers " + prequelId);
            currentId = prequelId;
        }
    }

    private List<String> buildOrderedList(String firstId) throws Exception {
        List<String> list = new ArrayList<>();
        String currentId  = firstId;
        Set<String> visited = new HashSet<>();
        while (currentId != null) {
            if (!visited.add(currentId))
                throw new IllegalStateException("Boucle détectée sur l'ID " + currentId);
            list.add(currentId);
            System.out.println("  → Saison " + list.size() + " : ID " + currentId);
            JsonNode root     = get(BASE + "/anime/" + currentId + "?include=" + INCLUDE);
            JsonNode included = root.path("included");
            currentId         = findNextInChain(included, "sequel", "prequel");
        }
        return list;
    }

    /**
     * Cherche dans les mediaRelationships inclus un role donné (prequel/sequel)
     * dont la destination est de type anime.
     */
    private String findRole(JsonNode included, String role) {
        for (JsonNode node : included) {
            if (!"mediaRelationships".equals(node.path("type").asText())) continue;
            if (!role.equals(node.path("attributes").path("role").asText())) continue;
            JsonNode dest = node.path("relationships").path("destination").path("data");
            if ("anime".equals(dest.path("type").asText()))
                return dest.path("id").asText();
        }
        return null;
    }

    /** Toutes les destinations (type anime) d'un rôle donné dans les mediaRelationships inclus. */
    private List<String> findAllRole(JsonNode included, String role) {
        List<String> ids = new ArrayList<>();
        for (JsonNode node : included) {
            if (!"mediaRelationships".equals(node.path("type").asText())) continue;
            if (!role.equals(node.path("attributes").path("role").asText())) continue;
            JsonNode dest = node.path("relationships").path("destination").path("data");
            if ("anime".equals(dest.path("type").asText()))
                ids.add(dest.path("id").asText());
        }
        return ids;
    }

    /** true si l'anime (subtype Kitsu, ex: "music") ne doit pas être importé comme saison. */
    private boolean isExcludedSubtype(JsonNode data) {
        String subtype = data.path("attributes").path("subtype").asText(null);
        return subtype != null && EXCLUDED_SUBTYPES.contains(subtype.toLowerCase());
    }

    /**
     * Toutes les destinations (type anime) des mediaRelationships dont le rôle
     * n'est ni "prequel" ni "sequel" — c-à-d les à-côtés de la saga principale
     * (spin-off, side_story, alternative_version, summary, other, adaptation…).
     * Sert à découvrir les hors-séries quand on importe uniquement depuis Kitsu
     * (sans page LiveChart pour les lister à l'avance).
     */
    private Set<String> findOtherAnimeRelations(JsonNode included) {
        Set<String> ids = new HashSet<>();
        for (JsonNode node : included) {
            if (!"mediaRelationships".equals(node.path("type").asText())) continue;
            String role = node.path("attributes").path("role").asText();
            if ("prequel".equals(role) || "sequel".equals(role)) continue;
            JsonNode dest = node.path("relationships").path("destination").path("data");
            if ("anime".equals(dest.path("type").asText()))
                ids.add(dest.path("id").asText());
        }
        return ids;
    }

    /**
     * Trouve l'anime "immédiatement" suivant dans la chaîne prequel/sequel.
     * Une saison peut avoir plusieurs relations du même rôle (ex: deux "sequel"),
     * alors que l'un des candidats est en réalité la suite de l'autre
     * (saison4 → sequel → A ET saison4 → sequel → B, alors que B est le sequel
     * de A). Dans ce cas on écarte les candidats qui sont eux-mêmes en aval
     * d'un autre candidat, pour ne garder que le maillon vraiment immédiat.
     */
    private String findNextInChain(JsonNode included, String role, String oppositeRole) throws Exception {
        List<String> candidates = findAllRole(included, role);
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        System.out.println("  ⚠ " + candidates.size() + " relations \"" + role + "\" trouvées, désambiguïsation…");

        List<String> immediate = new ArrayList<>();
        for (String candidate : candidates) {
            JsonNode candRoot     = get(BASE + "/anime/" + candidate + "?include=" + INCLUDE);
            JsonNode candIncluded = candRoot.path("included");
            String upstream       = findRole(candIncluded, oppositeRole);
            // Si ce candidat est précédé par un AUTRE candidat de la liste,
            // il n'est pas le maillon immédiat (il vient après un autre candidat).
            if (upstream != null && candidates.contains(upstream) && !upstream.equals(candidate)) {
                continue;
            }
            immediate.add(candidate);
        }

        if (immediate.size() == 1) {
            System.out.println("  ✓ Choix retenu : " + immediate.get(0));
            return immediate.get(0);
        }

        System.out.println("  ⚠ Désambiguïsation impossible (" + immediate.size()
                + " candidat(s) restant(s)), on garde le premier trouvé : " + candidates.get(0));
        return candidates.get(0);
    }

    // ── Construction d'une Season depuis data + included ─────────────────────

    private Season buildSeason(String kitsuId, JsonNode data, int watchIndex) {
        JsonNode a = data.get("attributes");
        Season s   = new Season();

        s.setIdKitsu(Integer.parseInt(kitsuId));
        s.setWatchIndex(watchIndex);

        JsonNode titles = a.get("titles");
        String canonical = a.path("canonicalTitle").asText(null);

        String nomFr = firstNonNull(titles, "en", "en_us", "en_jp");
        s.setNomFr(nomFr != null ? nomFr : canonical);

        String nomOrig = firstNonNull(titles, "en_jp", "ja_jp", "en_us");
        s.setNomOrig(nomOrig != null ? nomOrig : canonical);

        JsonNode poster = a.path("posterImage");
        if (!poster.isMissingNode())
            s.setImageUrl(firstNonNull(poster, "large", "medium", "original", "small" ));

        JsonNode cover = a.path("coverImage");
        if (!cover.isMissingNode())
            s.setImageBaniereUrl(firstNonNull(cover, "large", "original", "small"));

        if (!a.path("averageRating").isNull() && !a.path("averageRating").isMissingNode()) {
            double raw = a.get("averageRating").asDouble();
            s.setNote(BigDecimal.valueOf(Math.round(raw) / 10.0));
        }

        s.setJourPremiere(dayOf(a.path("startDate").asText(null)));
        s.setMoisPremiere(monthOf(a.path("startDate").asText(null)));
        s.setAnneePremiere(yearOf(a.path("startDate").asText(null)));

        s.setFormat(a.path("subtype").asText(null));
        s.setStatus(a.path("status").asText(null));
        // nb_episodes / durée moyenne / durée totale : calculées depuis les épisodes
        // sortis (voir Season.getNbEpisodes/getDureeMoyenne/getDureeTotale) plutôt
        // que depuis le total annoncé par Kitsu (episodeCount / episodeLength).

        return s;
    }

    // ── Utilitaires HTTP ──────────────────────────────────────────────────────

    private JsonNode get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.api+json")
                .header("Content-Type", "application/vnd.api+json")
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300)
            throw new RuntimeException("Kitsu API " + res.statusCode() + " — " + url);
        return JSON.readTree(res.body());
    }

    // ── Utilitaires date ──────────────────────────────────────────────────────

    private Byte dayOf(String date) {
        if (date == null || date.isBlank()) return null;
        String[] p = date.split("-");
        return p.length >= 3 ? Byte.parseByte(p[2]) : null;
    }

    private Byte monthOf(String date) {
        if (date == null || date.isBlank()) return null;
        String[] p = date.split("-");
        return p.length >= 2 ? Byte.parseByte(p[1]) : null;
    }

    private Short yearOf(String date) {
        if (date == null || date.isBlank()) return null;
        return Short.parseShort(date.split("-")[0]);
    }

    private String firstNonNull(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.path(k);
            if (!v.isNull() && !v.isMissingNode() && !v.asText().isBlank()) return v.asText();
        }
        return null;
    }

    private void enrichStudios(Season season, JsonNode included) {
        Map<String, String> producerNames = new HashMap<>();
        for (JsonNode node : included) {
            if ("producers".equals(node.path("type").asText()))
                producerNames.put(
                    node.path("id").asText(),
                    node.path("attributes").path("name").asText()
                );
        }
        // Clé = nom normalisé (même logique que Studio.normalize) pour dédupliquer
        // même en cas de casse/espacement différents (ex: "Madhouse" / "MADHOUSE")
        Map<String, Studio> studiosMap = new LinkedHashMap<>(); // LinkedHashMap pour garder l'ordre
        for (JsonNode node : included) {
            if (!"animeProductions".equals(node.path("type").asText())) continue;
            if (!"studio".equals(node.path("attributes").path("role").asText())) continue;
            String producerId = node.path("relationships").path("producer")
                    .path("data").path("id").asText();
            String nomBrut = producerNames.get(producerId);
            if (nomBrut == null || nomBrut.isBlank()) continue;

            String nom = Studio.normalize(nomBrut);
            if (studiosMap.containsKey(nom)) continue;

            System.out.println(season.getNomFr() + " : " + nom);

            Studio studio = studioRepository.findByNomIgnoreCase(nom)
                    .orElseGet(() -> studioRepository.save(new Studio(nom)));

            studiosMap.put(nom, studio);
        }
        season.setStudio(new ArrayList<>(studiosMap.values()));
    }

    private void enrichEpisodes(Season season, JsonNode included) {
        int index = 0;
        List<Episode> episodes = new ArrayList<>();

        for (JsonNode node : included) {
            if (!"episodes".equals(node.path("type").asText())) continue;
            Episode episode = parseEpisodeAttributes(node, index++);
            episode.setSeason(season);
            episodes.add(episode);
        }
        season.setEpisodes(episodes);
        System.out.println("    → " + index + " épisode(s) importé(s)");
    }

    /** Construit un Episode à partir d'un noeud "episodes" de l'API Kitsu (sans le rattacher à une saison). */
    private Episode parseEpisodeAttributes(JsonNode node, int index) {
        JsonNode a = node.path("attributes");

        Episode episode = new Episode();
        episode.setIdKitsu(Integer.parseInt(node.path("id").asText()));
        episode.setWatchIndex(index);

        // Titre : canonicalTitle en fallback si titles.en absent
        String nomFr = a.path("titles").path("en").asText(null);
        if (nomFr == null || nomFr.isBlank())
            nomFr = a.path("canonicalTitle").asText(null);
        episode.setNomFr(nomFr);
        episode.setNomOrig(a.path("canonicalTitle").asText(null));

        // Thumbnail : peut être null (cf. épisode 329887)
        JsonNode thumb = a.path("thumbnail");
        if (!thumb.isNull() && !thumb.isMissingNode())
            episode.setImage(thumb.path("original").asText(null));

        episode.setJourSortie(dayOf(a.path("airdate").asText(null)));
        episode.setMoisSortie(monthOf(a.path("airdate").asText(null)));
        episode.setAnneeSortie(yearOf(a.path("airdate").asText(null)));

        if (!a.path("length").isNull() && !a.path("length").isMissingNode())
            episode.setDuree(a.path("length").asInt());

        return episode;
    }

    /**
     * Met à jour les épisodes d'une saison déjà en base SANS écraser les
     * données personnelles (video_link, temps_viso) : les épisodes déjà
     * connus (même id_kitsu) sont rafraîchis en place (titre/date/durée/image),
     * les nouveaux sont ajoutés. Sauvegarde directement via episodeRepository.
     */
    private List<Episode> mergeEpisodesInPlace(Season season, JsonNode included) {
        Map<Integer, Episode> existingByKitsuId = season.getEpisodes().stream()
                .filter(e -> e.getIdKitsu() != null)
                .collect(Collectors.toMap(Episode::getIdKitsu, e -> e, (a, b) -> a));

        List<Episode> toSave = new ArrayList<>();
        int index = 0, added = 0, updated = 0;
        for (JsonNode node : included) {
            if (!"episodes".equals(node.path("type").asText())) continue;
            Episode fresh = parseEpisodeAttributes(node, index++);

            Episode target = existingByKitsuId.get(fresh.getIdKitsu());
            if (target == null) {
                fresh.setSeason(season);
                toSave.add(fresh);
                added++;
            } else {
                target.setWatchIndex(fresh.getWatchIndex());
                target.setNomFr(fresh.getNomFr());
                target.setNomOrig(fresh.getNomOrig());
                target.setImage(fresh.getImage());
                target.setJourSortie(fresh.getJourSortie());
                target.setMoisSortie(fresh.getMoisSortie());
                target.setAnneeSortie(fresh.getAnneeSortie());
                target.setDuree(fresh.getDuree());
                // video_link / temps_viso : jamais touchés
                toSave.add(target);
                updated++;
            }
        }
        if (!toSave.isEmpty()) episodeRepository.saveAll(toSave);
        System.out.println("    → " + updated + " épisode(s) rafraîchi(s), " + added + " nouveau(x)");
        return toSave;
    }

    /**
     * Calcule nb_episodes / duree_moyenne / duree_totale à partir des épisodes
     * effectivement sortis (date de sortie connue et déjà passée), et les
     * stocke sur la saison — plutôt que d'utiliser le total annoncé par Kitsu
     * (episodeCount / episodeLength), qui inclut les épisodes pas encore diffusés.
     */
    private void applyEpisodeStats(Season season) {
        applyEpisodeStats(season, season.getEpisodes());
    }

    private void applyEpisodeStats(Season season, List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return;

        java.time.LocalDate today = java.time.LocalDate.now();
        List<Episode> released = episodes.stream().filter(e -> {
            Byte  j = e.getJourSortie();
            Byte  m = e.getMoisSortie();
            Short a = e.getAnneeSortie();
            if (j == null || m == null || a == null || j <= 0 || m <= 0 || a <= 0) return false;
            try {
                return !java.time.LocalDate.of(a, m, j).isAfter(today);
            } catch (java.time.DateTimeException ex) {
                return false;
            }
        }).toList();

        season.setNbEpisodes(released.size());

        List<Integer> releasedDurees = released.stream()
                .map(Episode::getDuree).filter(java.util.Objects::nonNull).toList();
        List<Integer> pool = releasedDurees.isEmpty()
                ? episodes.stream().map(Episode::getDuree).filter(java.util.Objects::nonNull).toList()
                : releasedDurees;
        Integer dureeMoyenne = pool.isEmpty() ? null
                : (int) Math.round(pool.stream().mapToInt(Integer::intValue).average().orElse(0));
        season.setDureeMoyenne(dureeMoyenne);

        int dureeTotale = released.stream()
                .mapToInt(e -> e.getDuree() != null ? e.getDuree() : 0)
                .sum();
        season.setDureeTotale(dureeTotale);
    }

    private void enrichGenres(Season season, JsonNode data, JsonNode included) {
        if (season.getIdAnisearch() != null) {
            try {
                List<SeasonGenre> genres = scrapeAniSearchGenres(
                    season, String.valueOf(season.getIdAnisearch())
                );
                if (!genres.isEmpty()) {
                    season.getSeasonGenres().clear();
                    season.getSeasonGenres().addAll(genres);
                    return;
                }
            } catch (Exception e) {
                System.err.println("  ⚠ AniSearch genres échoué : " + e.getMessage());
            }
        }

        // Pas d'AniSearch (ou rien trouvé) : repli sur les catégories Kitsu.
        // Le premier de la liste devient le genre principal, les suivants
        // sont secondaires.
        List<SeasonGenre> kitsuGenres = extractKitsuGenres(season, data, included);
        if (!kitsuGenres.isEmpty()) {
            season.getSeasonGenres().clear();
            season.getSeasonGenres().addAll(kitsuGenres);
            System.out.println("    → " + kitsuGenres.size() + " genre(s) récupéré(s) depuis Kitsu (catégories)");
        }
    }

    /**
     * Construit la liste des genres depuis les "categories" Kitsu de l'anime
     * (relations déjà chargées via INCLUDE), dans l'ordre renvoyé par l'API :
     * le premier devient le genre principal, les autres sont secondaires.
     */
    private List<SeasonGenre> extractKitsuGenres(Season season, JsonNode data, JsonNode included) {
        JsonNode catRefs = data.path("relationships").path("categories").path("data");
        if (!catRefs.isArray() || catRefs.isEmpty()) return List.of();

        Map<String, String> titleById = new HashMap<>();
        for (JsonNode node : included) {
            if ("categories".equals(node.path("type").asText())) {
                String title = node.path("attributes").path("title").asText(null);
                if (title != null && !title.isBlank())
                    titleById.put(node.path("id").asText(), title);
            }
        }

        List<SeasonGenre> result = new ArrayList<>();
        boolean first = true;
        for (JsonNode ref : catRefs) {
            String title = titleById.get(ref.path("id").asText());
            if (title == null) continue;

            Genre genre = genreRepository.findByNom(title)
                    .orElseGet(() -> genreRepository.save(new Genre(title)));
            result.add(new SeasonGenre(season, genre, first));
            first = false;
        }
        return result;
    }

    private void logSeason(Season season, int index, int total) {
        String pos = total > 0 ? "[" + index + "/" + total + "]" : "[HS " + index + "]";
        System.out.println("  " + pos + " Titre  : " + season.getNomFr());
        System.out.println("  " + pos + " Saga   : " + (season.isSagaPrincipale() ? "principale" : "hors-série"));
        System.out.println("  " + pos + " Format : " + season.getFormat()
                + " — " + season.getNbEpisodes() + " ep × " + season.getDureeMoyenne() + " min");
        System.out.println("  " + pos + " Genres : "
                + (season.getSeasonGenres() != null ? season.getSeasonGenres().size() : 0));
    }

    /**
     * Scrape les genres depuis https://www.anisearch.com/anime/<idAnisearch>
     * Catégories "Main" → is_main_genre = 1 ; "Subsidiary" → 0
     */
    private List<SeasonGenre> scrapeAniSearchGenres(Season season, String anisearchId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("https://www.anisearch.com/anime/" + anisearchId))
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("User-Agent", "Mozilla/5.0")
        .GET().build();
        
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300)
            throw new RuntimeException("AniSearch HTTP " + res.statusCode());
        
        String body = res.body();
        List<SeasonGenre> result = new ArrayList<>();

        // Parcourt les <li> du HTML fourni
        // Main    : <a href="anime/genre/main/..."    class="gg ...">
        // Subsidiary : <a href="anime/genre/subsidiary/..." class="gc ...">
        // Tag (ignoré) : <a href="anime/genre/tag/..."  class="gt ...">
        String[] lines = body.split("<li");
        for (String block : lines) {
            boolean isMain;
            if (block.contains("anime/genre/main/"))
                isMain = true;
            else if (block.contains("anime/genre/subsidiary/"))
                isMain = false;
            else {
                continue; // tag ou autre → ignoré
            }
            int end   = block.indexOf("</a>");
            if (end < 0) continue;

            // Dernier "> avant </a> = fermeture de la balise <a ...>
            int start = block.lastIndexOf("\">", end);
            if (start < 0 || start + 2 >= end) continue;

            String nom = block.substring(start + 2, end).trim();

            if (nom.isBlank()) continue;
            System.out.println(nom);

            Genre genre = genreRepository.findByNom(nom)
                    .orElseGet(() -> genreRepository.save(new Genre(nom)));
            result.add(new SeasonGenre(season, genre, isMain));
        }

        System.out.println("    → " + result.size() + " genres récupérés depuis AniSearch");
        return result;
    }

    // ── Utilitaires texte ─────────────────────────────────────────────────────
    public static String normalize(String input) {
        if (input == null) return null;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        normalized = normalized.toLowerCase();
        return normalized.replaceAll("[^a-z0-9]", "");
    }
}