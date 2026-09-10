package com.animelist.repository;

import com.animelist.model.SeasonGenre;
import com.animelist.model.SeasonGenreId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SeasonGenreRepository extends JpaRepository<SeasonGenre, SeasonGenreId> {

    /**
     * Noms distincts des genres ayant au moins une relation "principale"
     * (is_main_genre = true), tous saisons/séries confondues.
     */
    @Query("""
        SELECT DISTINCT g.nom
        FROM SeasonGenre sg
        JOIN sg.genre g
        WHERE sg.isMainGenre = true
        ORDER BY g.nom ASC
        """)
    List<String> findGenreNamesUsedAsMain();

    /**
     * Noms distincts des genres n'ayant jamais de relation "principale"
     * (utilisés uniquement en tant que genre secondaire).
     */
    @Query("""
        SELECT DISTINCT g.nom
        FROM SeasonGenre sg
        JOIN sg.genre g
        WHERE sg.isMainGenre = false
          AND g.nom NOT IN (
              SELECT DISTINCT g2.nom
              FROM SeasonGenre sg2
              JOIN sg2.genre g2
              WHERE sg2.isMainGenre = true
          )
        ORDER BY g.nom ASC
        """)
    List<String> findGenreNamesSecondaryOnly();
}
