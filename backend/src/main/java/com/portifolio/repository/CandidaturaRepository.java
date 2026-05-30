package com.portifolio.repository;

import com.portifolio.model.Candidatura;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidaturaRepository extends JpaRepository<Candidatura, Long> {
    List<Candidatura> findByVagaId(Long vagaId);
    List<Candidatura> findByArtistaUsuarioId(Long usuarioId);
    boolean existsByVagaIdAndArtistaUsuarioId(Long vagaId, Long usuarioId);
}
