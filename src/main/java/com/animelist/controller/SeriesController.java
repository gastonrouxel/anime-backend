package com.animelist.controller;

import com.animelist.model.LiveChartResult;
import com.animelist.model.Season;
import com.animelist.model.Serie;
import com.animelist.service.LiveChartScraperService;
import com.animelist.service.SeasonService;
import com.animelist.service.SeriesService;
import com.animelist.service.KitsuImportService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/series")
public class SeriesController {

    private final SeriesService             seriesService;
    private final SeasonService             seasonService;
    private final LiveChartScraperService   scraper;
    private final KitsuImportService        kitsuImportService;

    public SeriesController(SeriesService seriesService,
                            SeasonService seasonService,
                            LiveChartScraperService scraper,
                            KitsuImportService kitsuImportService) {
        this.seriesService      = seriesService;
        this.seasonService      = seasonService;
        this.scraper            = scraper;
        this.kitsuImportService = kitsuImportService;
    }

    // ── GET /api/series ──────────────────────────────────────────────────────
    @GetMapping
    public List<Serie> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order) {
        return seriesService.getAll(search, sortBy, order);
    }

    // ── GET /api/series/genres ───────────────────────────────────────────────
    // Liste de tous les genres connus, pour peupler le filtre par genre.
    @GetMapping("/genres")
    public List<String> getAllGenres() {
        return seriesService.getAllGenreNames();
    }

    // ── GET /api/series/genres/main ──────────────────────────────────────────
    // Genres ayant au moins une relation "principale" (picker genre principal).
    @GetMapping("/genres/main")
    public List<String> getMainGenres() {
        return seriesService.getGenreNamesUsedAsMain();
    }

    // ── GET /api/series/genres/secondary ─────────────────────────────────────
    // Genres n'ayant jamais de relation "principale" (picker genre secondaire).
    @GetMapping("/genres/secondary")
    public List<String> getSecondaryOnlyGenres() {
        return seriesService.getGenreNamesSecondaryOnly();
    }

    // ── GET /api/series/{id} ─────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(seriesService.getById(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET /api/series/{id}/seasons ─────────────────────────────────────────
    @GetMapping("/{id}/seasons")
    public List<Season> getSeasons(
            @PathVariable Integer id,
            @RequestParam(required = false) String search) {
        return seasonService.getBySerie(id, search);
    }

    // ── GET /api/series/search-livechart?q=naruto ────────────────────────────
    // Lance un scraping Playwright et retourne les résultats LiveChart bruts.
    @GetMapping("/search-livechart")
    public ResponseEntity<?> searchLivechart(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Paramètre q manquant"));
        }
        try {
            List<LiveChartResult> results = scraper.search(q.trim());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Scraping échoué : " + e.getMessage()));
        }
    }

    // ── GET /api/series/search-kitsu?q=naruto ────────────────────────────────
    // Recherche directement sur l'API Kitsu (utile pour les animes occidentaux
    // absents de LiveChart).
    @GetMapping("/search-kitsu")
    public ResponseEntity<?> searchKitsu(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Paramètre q manquant"));
        }
        try {
            List<LiveChartResult> results = kitsuImportService.search(q.trim());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Recherche Kitsu échouée : " + e.getMessage()));
        }
    }

    // ── GET /api/series/livechart-detail/{id} ────────────────────────────────
    // Scrape la page détail d'un anime : franchise ID + Kitsu ID/URL
    @GetMapping("/livechart-detail/{id}")
    public ResponseEntity<?> getLiveChatFranchise(@PathVariable String id) {
        try {
            List<Season> detail = scraper.getSeasons(id);
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Scraping échoué : " + e.getMessage()));
        }
    }

    // ── POST /api/series/{id} ────────────────────────────────────────────────
    // Met à jour une série déjà importée sans écraser les données existantes
    // (nouvelles saisons/hors-séries, statuts, prochains épisodes, épisodes...).
    @PostMapping("/{id}")
    public ResponseEntity<?> updateSerie(@PathVariable Integer id) {
        try {
            kitsuImportService.updateSerie(id);
            return ResponseEntity.ok(Map.of("message", "Mise à jour terminée"));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }


    // ── POST /api/series/check-duplicate ────────────────────────────────────
    // Body : { "kitsuIds": [...], "livechartIds": [...] }
    // Retourne la Serie existante (200) ou 204 si aucun doublon
    @PostMapping("/check-duplicate")
    public ResponseEntity<?> checkDuplicate(@RequestBody Map<String, List<Integer>> body) {
        List<Integer> kitsuIds      = body.getOrDefault("kitsuIds",      List.of());
        List<Integer> livechartIds  = body.getOrDefault("livechartIds",  List.of());
        return seriesService.checkDuplicate(kitsuIds, livechartIds)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // ── POST /api/series?toWatch=true ────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> importKitsu(
            @RequestBody List<Season> seasons,
            @RequestParam(required = false, defaultValue = "false") boolean toWatch) {
        try {
            kitsuImportService.importSerie(seasons, toWatch);
            return ResponseEntity.ok(Map.of("message", "Import terminé"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── DELETE /api/series/{id} ──────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        try {
            seriesService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
