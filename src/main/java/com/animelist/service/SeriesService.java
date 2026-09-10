package com.animelist.service;

import com.animelist.model.Genre;
import com.animelist.model.Season;
import com.animelist.model.Serie;
import com.animelist.repository.GenreRepository;
import com.animelist.repository.SeasonRepository;
import com.animelist.repository.SerieRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SeriesService {

    private final SerieRepository repo;
    private final SeasonRepository seasonRepository;
    private final GenreRepository genreRepository;
    private final com.animelist.repository.SeasonGenreRepository seasonGenreRepository;

    public SeriesService(SerieRepository repo, SeasonRepository seasonRepository, GenreRepository genreRepository,
                          com.animelist.repository.SeasonGenreRepository seasonGenreRepository) {
        this.repo                  = repo;
        this.seasonRepository       = seasonRepository;
        this.genreRepository        = genreRepository;
        this.seasonGenreRepository = seasonGenreRepository;
    }

    /** Liste triée des noms de tous les genres connus (pour peupler les filtres). */
    public List<String> getAllGenreNames() {
        return genreRepository.findAll().stream()
                .map(Genre::getNom)
                .filter(java.util.Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** Genres ayant au moins une relation "principale" (pour le picker de genre principal). */
    public List<String> getGenreNamesUsedAsMain() {
        return seasonGenreRepository.findGenreNamesUsedAsMain();
    }

    /** Genres n'ayant jamais de relation "principale" (pour le picker de genre secondaire). */
    public List<String> getGenreNamesSecondaryOnly() {
        return seasonGenreRepository.findGenreNamesSecondaryOnly();
    }

    public List<Serie> getAll(String search, String sortBy, String order) {
        List<Serie> list;

        if (search != null && !search.isBlank()) {
            list = repo.searchByName(search.trim());
        } else {
            list = repo.findAll();
        }

        Map<Integer, BigDecimal> notes = seasonRepository.avgNoteGroupedBySerie()
                .stream()
                .collect(Collectors.toMap(
                        row -> (Integer)   row[0],
                        row -> row[1] == null ? null
                             : BigDecimal.valueOf((Double) row[1])
                ));
 
        list.forEach(s -> s.setNote(notes.get(s.getId())));

        Map<Integer, String> dates = seasonRepository.datePremiereBySerie()
                .stream()
                .collect(Collectors.toMap(
                        row -> (Integer)   row[0],
                        row -> formatDate(row[1], row[2], row[3])
                ));
 
        list.forEach(s -> s.setDate(dates.get(s.getId())));

        Map<Integer, String> statuses = seasonRepository.statusDerniereSaisonBySerie()
                .stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> (String)  row[1]
                ));

        list.forEach(s -> s.setStatus(statuses.get(s.getId())));

        Map<Integer, Integer> episodesTotals = seasonRepository.sumEpisodesGroupedBySerie()
                .stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> row[1] == null ? null : ((Number) row[1]).intValue()
                ));

        list.forEach(s -> s.setEpisodes(episodesTotals.get(s.getId())));

        Map<Integer, List<String>> genresPrincipalBySerie = seasonRepository.genresPrincipauxGroupedBySerie()
                .stream()
                .collect(Collectors.groupingBy(
                        row -> (Integer) row[0],
                        Collectors.mapping(row -> (String) row[1], Collectors.toList())
                ));

        Map<Integer, List<String>> genresSecondairesBySerie = seasonRepository.genresSecondairesGroupedBySerie()
                .stream()
                .collect(Collectors.groupingBy(
                        row -> (Integer) row[0],
                        Collectors.mapping(row -> (String) row[1], Collectors.toList())
                ));

        list.forEach(s -> {
            List<String> principal   = genresPrincipalBySerie.getOrDefault(s.getId(), List.of());
            List<String> secondaires = genresSecondairesBySerie.getOrDefault(s.getId(), List.of());
            s.setGenresPrincipal(principal);
            s.setGenresSecondaires(secondaires);
            // Affichage (cartes/tableau) : uniquement les genres principaux, comme avant.
            s.setGenres(principal);
        });

        Comparator<Serie> comparator = switch (sortBy == null ? "" : sortBy) {
            case "nomFr" -> Comparator.comparing(Serie::getNomFr,
                                Comparator.nullsLast(String::compareToIgnoreCase));
            case "note" -> Comparator.comparing(Serie::getNote,
                                Comparator.nullsFirst(Comparator.naturalOrder()));
            case "nbEpisodes" -> Comparator.comparing(Serie::getEpisodes,
                                Comparator.nullsFirst(Comparator.naturalOrder()));
            default      -> Comparator.comparing(Serie::getId,
                                Comparator.nullsLast(Integer::compareTo));
        };

        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }

        list.sort(comparator);
        return list;
    }

    private String formatDate(Object jour, Object mois, Object annee) {
        String j = (jour  != null && ((Number) jour).intValue()  > 0) ? String.format("%02d", ((Number) jour).intValue())  : "--";
        String m = (mois  != null && ((Number) mois).intValue()  > 0) ? String.format("%02d", ((Number) mois).intValue())  : "--";
        String a = (annee != null && ((Number) annee).intValue() > 0) ? String.valueOf(((Number) annee).intValue()) : "--";
        return j + "/" + m + "/" + a;
    }

    public Serie getById(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Série introuvable : " + id));
    }
    
    /**
     * Vérifie si une série contenant déjà une de ces saisons existe.
     * Cherche par idKitsu ET par idLivechart dans la table seasons.
     * Retourne la Serie parente si trouvée, empty sinon.
     */
    public Optional<Serie> checkDuplicate(List<Integer> kitsuIds, List<Integer> livechartIds) {
        // Cherche par Kitsu
        if (kitsuIds != null && !kitsuIds.isEmpty()) {
            Optional<Season> bySeason = seasonRepository.findFirstByIdKitsuIn(kitsuIds);
            if (bySeason.isPresent()) {
                return repo.findById(bySeason.get().getIdSerie());
            }
        }
        // Cherche par Livechart
        if (livechartIds != null && !livechartIds.isEmpty()) {
            Optional<Season> bySeason = seasonRepository.findFirstByIdLivechartIn(livechartIds);
            if (bySeason.isPresent()) {
                return repo.findById(bySeason.get().getIdSerie());
            }
        }
        return Optional.empty();
    }

    @Transactional
    public Serie add(Serie serie) {
        if (serie.getNomFr() == null || serie.getNomFr().isBlank()) {
            throw new IllegalArgumentException("nom_fr est obligatoire");
        }
        return repo.save(serie);
    }

    @Transactional
    public void delete(Integer id) {
        if (!repo.existsById(id))
            throw new EntityNotFoundException("Série introuvable : " + id);

        seasonRepository.deleteByIdSerie(id);
        repo.deleteById(id);
    }
}
