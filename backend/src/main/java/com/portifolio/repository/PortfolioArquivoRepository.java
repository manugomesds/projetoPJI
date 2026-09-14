package com.portifolio.repository;

import com.portifolio.model.PortfolioArquivo;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface PortfolioArquivoRepository extends JpaRepository<PortfolioArquivo, Long> {
    Page<PortfolioArquivo> findByArtistaUsuarioId(Long artistaId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PortfolioArquivo a where a.id = :id and a.artista.usuarioId = :dono")
    Optional<PortfolioArquivo> bloquearProprio(Long id, Long dono);
}
