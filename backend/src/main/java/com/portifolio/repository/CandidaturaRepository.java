package com.portifolio.repository;

import com.portifolio.model.Candidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.repository.projection.CandidaturaDashboardProjection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidaturaRepository extends JpaRepository<Candidatura, Long> {
    List<Candidatura> findByVagaId(Long vagaId);
    List<Candidatura> findByArtistaUsuarioId(Long usuarioId);
    List<Candidatura> findByVagaContratanteUsuarioId(Long usuarioId);
    boolean existsByVagaIdAndArtistaUsuarioId(Long vagaId, Long usuarioId);
    boolean existsByArtistaUsuarioIdAndVagaContratanteUsuarioId(
            Long artistaId, Long contratanteId);
    Optional<Candidatura> findByVagaIdAndArtistaUsuarioId(Long vagaId, Long usuarioId);

    @Query("""
            select c.artista.usuarioId
            from Candidatura c
            where c.vaga.id = :vagaId
              and (:cursor is null or c.artista.usuarioId > :cursor)
            order by c.artista.usuarioId
            """)
    List<Long> findArtistaUsuarioIdsByVagaIdAposCursor(
            @Param("vagaId") Long vagaId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    @EntityGraph(attributePaths = {"vaga", "artista"})
    Page<Candidatura> findByArtistaUsuarioId(Long usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = {"vaga", "artista"})
    Page<Candidatura> findByVagaContratanteUsuarioId(Long usuarioId, Pageable pageable);

    @Query(value = """
            select c.id
            from Candidatura c
            left join c.artista.tags tag
            where c.vaga.id = :vagaId
            group by c.id, c.artista.ultimaAtualizacao
            order by sum(case when tag.id in :tagIds then 1 else 0 end) desc,
                     case when c.artista.ultimaAtualizacao is null then 1 else 0 end asc,
                     c.artista.ultimaAtualizacao desc,
                     c.id asc
            """, countQuery = """
            select count(c.id)
            from Candidatura c
            where c.vaga.id = :vagaId
            """)
    Page<Long> findIdsPorVagaOrdenadosPorCompatibilidade(
            @Param("vagaId") Long vagaId,
            @Param("tagIds") Set<Long> tagIds,
            Pageable pageable);

    @EntityGraph(attributePaths = {"artista", "artista.usuario", "artista.tags"})
    @Query("select distinct c from Candidatura c where c.id in :ids")
    List<Candidatura> findDetalhadasByIdIn(@Param("ids") List<Long> ids);

    // RF03 Fase 2 — candidaturas do artista em vagas que foram canceladas (RF25),
    // usado para decidir quais vagas CANCELADA ainda devem aparecer para ele.
    List<Candidatura> findByArtista_UsuarioIdAndVaga_Status(Long usuarioId, StatusVaga status);

    @Query("""
            select c.vaga.id
            from Candidatura c
            where c.artista.usuarioId = :artistaId
              and c.vaga.status = :status
              and (:cursor is null or c.vaga.id > :cursor)
            order by c.vaga.id asc
            """)
    List<Long> findVagaIdsDoArtistaPorStatusAposCursor(
            @Param("artistaId") Long artistaId,
            @Param("status") StatusVaga status,
            @Param("cursor") Long cursor,
            Pageable pageable);

    @Query(value = """
            select c.id as id,
                   vaga.id as vagaId,
                   vaga.titulo as tituloVaga,
                   artista.usuarioId as artistaId,
                   usuario.nome as nomeArtista,
                   artista.fotoPerfil as fotoPerfilArtista,
                   usuario.fotoPerfil as fotoPerfilUsuario,
                   c.status as status,
                   c.dataCandidatura as dataCandidatura
            from Candidatura c
            join c.vaga vaga
            join c.artista artista
            join artista.usuario usuario
            where vaga.contratante.usuarioId = :contratanteId
              and vaga.status in :statusAtivos
            order by c.dataCandidatura desc, c.id desc
            """, countQuery = """
            select count(c.id)
            from Candidatura c
            join c.vaga vaga
            where vaga.contratante.usuarioId = :contratanteId
              and vaga.status in :statusAtivos
            """)
    Page<CandidaturaDashboardProjection> findRecentesDoContratanteEmVagasAtivas(
            @Param("contratanteId") Long contratanteId,
            @Param("statusAtivos") Set<StatusVaga> statusAtivos,
            Pageable pageable);
}
