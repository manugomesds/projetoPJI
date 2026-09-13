package com.portifolio.repository;

import com.portifolio.model.PerfilContratante;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerfilContratanteRepository extends JpaRepository<PerfilContratante, Long> {

    @EntityGraph(attributePaths = "usuario.responsavelLegal")
    @Query("select perfil from PerfilContratante perfil where perfil.usuarioId = :usuarioId")
    Optional<PerfilContratante> buscarPublicoPorUsuarioId(@Param("usuarioId") Long usuarioId);
}
