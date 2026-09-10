package com.animelist.repository;

import com.animelist.model.Studio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudioRepository extends JpaRepository<Studio, Integer> {

    Optional<Studio> findByNom(String nom);

    // Recherche insensible à la casse, indépendante de la collation de la base
    // (utile si la collation MySQL change ou diffère entre environnements).
    @Query("SELECT s FROM Studio s WHERE LOWER(s.nom) = LOWER(:nom)")
    Optional<Studio> findByNomIgnoreCase(@Param("nom") String nom);
}