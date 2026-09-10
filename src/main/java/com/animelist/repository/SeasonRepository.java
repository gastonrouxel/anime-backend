package com.animelist.repository;

import com.animelist.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Integer> {

    /** Toutes les saisons d'une série. */
    List<Season> findByIdSerie(Integer idSerie);

    /** Recherche texte dans les saisons d'une série. */
    @Query("""
        SELECT s FROM Season s
        WHERE s.idSerie = :idSerie
          AND (LOWER(s.nomFr) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.nomOrig) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    List<Season> searchInSerie(@Param("idSerie") Integer idSerie,
                               @Param("query") String query);

    void deleteByIdSerie(Integer idSerie);

    /** Doublon : une saison avec un de ces idKitsu existe déjà ? */
    Optional<Season> findFirstByIdKitsuIn(List<Integer> ids);

    /** Doublon : une saison avec un de ces idLivechart existe déjà ? */
    Optional<Season> findFirstByIdLivechartIn(List<Integer> ids);

    /**
     * Retourne la moyenne des notes des saisons principales (saga_principale = true)
     * pour toutes les séries en une seule requête.
     * Résultat : liste de Object[] { idSerie (Integer), avgNote (BigDecimal) }
     */
    @Query("""
        SELECT s.idSerie, AVG(s.note)
        FROM Season s
        WHERE s.sagaPrincipale = true AND s.note IS NOT NULL
        GROUP BY s.idSerie
        """)
    List<Object[]> avgNoteGroupedBySerie();

    @Query(value = """
        SELECT id_serie, jour_sortie, mois_sortie, annee_sortie
        FROM (
            SELECT s.id_serie, s.jour_sortie, s.mois_sortie, s.annee_sortie,
                ROW_NUMBER() OVER (
                    PARTITION BY s.id_serie
                    ORDER BY s.annee_sortie DESC,
                             s.mois_sortie  DESC,
                             s.jour_sortie  DESC
                ) AS rn
            FROM seasons s
            WHERE s.saga_principale = true
        ) t
        WHERE rn = 1
        """, nativeQuery = true)
    List<Object[]> datePremiereBySerie();

    /**
     * Status de la dernière saison de la trame principale (saga_principale = true)
     * pour chaque série, la "dernière" étant déterminée par la date de sortie
     * la plus récente (même logique que datePremiereBySerie()).
     * Résultat : liste de Object[] { idSerie (Integer), status (String) }
     */
    @Query(value = """
        SELECT id_serie, status
        FROM (
            SELECT s.id_serie, s.status,
                ROW_NUMBER() OVER (
                    PARTITION BY s.id_serie
                    ORDER BY s.annee_sortie DESC,
                             s.mois_sortie  DESC,
                             s.jour_sortie  DESC
                ) AS rn
            FROM seasons s
            WHERE s.saga_principale = true
        ) t
        WHERE rn = 1
        """, nativeQuery = true)
    List<Object[]> statusDerniereSaisonBySerie();

    /**
     * Somme du nombre d'épisodes de toutes les saisons de la trame principale
     * (saga_principale = true), groupée par série.
     * Résultat : liste de Object[] { idSerie (Integer), totalEpisodes (Long) }
     */
    @Query("""
        SELECT s.idSerie, SUM(s.nbEpisodes)
        FROM Season s
        WHERE s.sagaPrincipale = true AND s.nbEpisodes IS NOT NULL
        GROUP BY s.idSerie
        """)
    List<Object[]> sumEpisodesGroupedBySerie();

    /**
     * Genres principaux distincts de l'ensemble des saisons de la trame
     * principale, groupés par série.
     * Résultat : liste de Object[] { idSerie (Integer), nomGenre (String) }
     */
    @Query("""
        SELECT DISTINCT s.idSerie, g.nom
        FROM Season s
        JOIN s.seasonGenres sg
        JOIN sg.genre g
        WHERE s.sagaPrincipale = true AND sg.isMainGenre = true
        """)
    List<Object[]> genresPrincipauxGroupedBySerie();

    /**
     * Genres secondaires distincts de l'ensemble des saisons de la trame
     * principale, groupés par série.
     * Résultat : liste de Object[] { idSerie (Integer), nomGenre (String) }
     */
    @Query("""
        SELECT DISTINCT s.idSerie, g.nom
        FROM Season s
        JOIN s.seasonGenres sg
        JOIN sg.genre g
        WHERE s.sagaPrincipale = true AND sg.isMainGenre = false
        """)
    List<Object[]> genresSecondairesGroupedBySerie();
}