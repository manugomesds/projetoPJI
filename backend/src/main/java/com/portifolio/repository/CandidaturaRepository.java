package com.portifolio.repository;

import com.portifolio.model.Candidatura;
import com.portifolio.model.enums.StatusVaga;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidaturaRepository extends JpaRepository<Candidatura, Long> {
    List<Candidatura> findByVagaId(Long vagaId);
    List<Candidatura> findByArtistaUsuarioId(Long usuarioId);
    List<Candidatura> findByVagaContratanteUsuarioId(Long usuarioId);
    Page<Candidatura> findByArtistaUsuarioId(Long usuarioId, Pageable pageable);
    Page<Candidatura> findByVagaContratanteUsuarioId(Long usuarioId, Pageable pageable);
    boolean existsByVagaIdAndArtistaUsuarioId(Long vagaId, Long usuarioId);

    // RF03 Fase 2 — candidaturas do artista em vagas que foram canceladas (RF25),
    // usado para decidir quais vagas CANCELADA ainda devem aparecer para ele.
    List<Candidatura> findByArtista_UsuarioIdAndVaga_Status(Long usuarioId, StatusVaga status);
}
