package com.animelist.service;

import com.animelist.model.Season;
import com.animelist.repository.SeasonRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeasonService {

    private final SeasonRepository repo;

    public SeasonService(SeasonRepository repo) {
        this.repo = repo;
    }

    public List<Season> getBySerie(Integer idSerie, String search) {
        if (search != null && !search.isBlank()) {
            return repo.searchInSerie(idSerie, search.trim());
        }
        return repo.findByIdSerie(idSerie);
    }

    public Season getById(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Saison introuvable : " + id));
    }

    @Transactional
    public Season add(Integer idSerie, Season season) {
        if (repo.existsById(season.getId())) {
            throw new IllegalArgumentException("Saison déjà existante : " + season.getId());
        }
        season.setIdSerie(idSerie);
        
        return repo.save(season);
    }

    @Transactional
    public void delete(Integer id) {
        if (!repo.existsById(id)) {
            throw new EntityNotFoundException("Saison introuvable : " + id);
        }
        repo.deleteById(id);
    }

   @Transactional
    public void deleteByIdSerie(Integer idSerie) {
        List<Season> seasons = repo.findByIdSerie(idSerie);
        if (seasons.isEmpty()) return;
 
        for (Season s : seasons) {
            s.getStudio().clear();
            repo.save(s);
        }
 
        repo.deleteAll(seasons);
    }

}
