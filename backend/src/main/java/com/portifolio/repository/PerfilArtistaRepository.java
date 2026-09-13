package com.portifolio.repository;

import com.portifolio.model.PerfilArtista;
import com.portifolio.repository.projection.TalentoSugeridoProjection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
            select pa.usuario_id as usuarioId,
                   count(distinct ta.funcao_id) as quantidadeFuncoesCoincidentes
            from perfis_artistas pa
            join usuarios u on u.id = pa.usuario_id
            join perfil_artista_funcao ta on ta.perfil_artista_id = pa.usuario_id
            where u.tipo_usuario = 'ARTISTA'
              and u.perfil_completo = true
              and u.data_nascimento <= current_date - interval '18 years'
              and ta.funcao_id in (:funcaoIds)
            group by pa.usuario_id, pa.ultima_atualizacao
            order by count(distinct ta.funcao_id) desc,
                     pa.ultima_atualizacao desc nulls last,
                     pa.usuario_id asc
            """, countQuery = """
            select count(distinct pa.usuario_id)
            from perfis_artistas pa
            join usuarios u on u.id = pa.usuario_id
            join perfil_artista_funcao ta on ta.perfil_artista_id = pa.usuario_id
            where u.tipo_usuario = 'ARTISTA'
              and u.perfil_completo = true
              and u.data_nascimento <= current_date - interval '18 years'
              and ta.funcao_id in (:funcaoIds)
            """, nativeQuery = true)
    Page<TalentoSugeridoProjection> findSugeridosPorFuncoes(
            @Param("funcaoIds") Set<Long> funcaoIds,
            Pageable pageable);
}
