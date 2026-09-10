package com.animelist.controller;

import com.animelist.model.Episode;
import com.animelist.repository.EpisodeRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seasons")
public class SeasonController {

    private final EpisodeRepository episodeRepository;

    public SeasonController(EpisodeRepository episodeRepository) {
        this.episodeRepository = episodeRepository;
    }

    // ── GET /api/seasons/{id}/episodes ───────────────────────────────────────
    @GetMapping("/{id}/episodes")
    public List<Episode> getEpisodes(@PathVariable Integer id) {
        return episodeRepository.findBySeasonIdOrderByWatchIndex(id);
    }
}
