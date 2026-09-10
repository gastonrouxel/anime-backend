package com.animelist.repository;

import com.animelist.model.Episode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EpisodeRepository extends JpaRepository<Episode, Integer> {
    List<Episode>    findBySeasonIdOrderByWatchIndex(Integer idSeason);
    Optional<Episode> findByIdKitsu(Integer idKitsu);
}