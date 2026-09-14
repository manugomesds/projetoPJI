package com.portifolio.repository;

import com.portifolio.model.PerfilArtista;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerfilArtistaRepository extends JpaRepository<PerfilArtista, Long> {

    @EntityGraph(attributePaths = {"usuario.responsavelLegal", "areas.funcoes"})
    @Query("select distinct perfil from PerfilArtista perfil where perfil.usuarioId = :usuarioId")
    Optional<PerfilArtista> buscarPublicoPorUsuarioId(@Param("usuarioId") Long usuarioId);

    @EntityGraph(attributePaths = {"usuario.responsavelLegal", "areas.funcoes"})
    @Query("select distinct perfil from PerfilArtista perfil where perfil.usuarioId in :usuarioIds")
    List<PerfilArtista> buscarPublicosPorUsuarioIds(@Param("usuarioIds") List<Long> usuarioIds);

}
