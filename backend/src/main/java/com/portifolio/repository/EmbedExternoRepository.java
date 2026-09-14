package com.portifolio.repository;

import com.portifolio.model.EmbedExterno;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbedExternoRepository extends JpaRepository<EmbedExterno, Long> {
    Page<EmbedExterno> findByArtistaUsuarioId(Long artistaId, Pageable pageable);
    Optional<EmbedExterno> findByIdAndArtistaUsuarioId(Long id, Long artistaId);
}
