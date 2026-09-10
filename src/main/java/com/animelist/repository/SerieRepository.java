package com.animelist.repository;

import com.animelist.model.Serie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SerieRepository extends JpaRepository<Serie, Integer> {

    @Query("""
        SELECT s FROM Serie s
        WHERE LOWER(s.nomFr) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(s.nomOrig) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    List<Serie> searchByName(@Param("query") String query);

    Optional<Serie> findByIdFranchise(Integer idFranchise);

    Optional<Serie> findByIdFirstEltLiveChart(Integer idFirstEltLiveChart);
}
