package com.portifolio.repository;

import com.portifolio.model.Vaga;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.repository.projection.VagaPrazoProjection;
import com.portifolio.repository.projection.VagaRecomendadaProjection;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VagaRepository extends JpaRepository<Vaga, Long>, JpaSpecificationExecutor<Vaga> {

    List<Vaga> findByTituloContainingIgnoreCase(String titulo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vaga v where v.id = :id")
    Optional<Vaga> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select v.id as id, v.titulo as titulo
            from Vaga v
            where v.status in :statusElegiveis
              and v.dataLimiteCandidatura is not null
              and v.dataLimiteCandidatura <= :hoje
            order by v.id
            """)
    List<VagaPrazoProjection> findElegiveisParaEncerramento(
            @Param("statusElegiveis") Set<StatusVaga> statusElegiveis,
            @Param("hoje") LocalDate hoje,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Vaga v
               set v.status = :statusEncerrado
             where v.id = :id
               and v.status in :statusElegiveis
               and v.dataLimiteCandidatura is not null
               and v.dataLimiteCandidatura <= :hoje
            """)
    int encerrarSeElegivelEVencida(
            @Param("id") Long id,
            @Param("hoje") LocalDate hoje,
            @Param("statusElegiveis") Set<StatusVaga> statusElegiveis,
            @Param("statusEncerrado") StatusVaga statusEncerrado);

    // RNF05: carrega tags (ManyToMany) e contratante junto, evitando N+1.
    // Usado APÓS a paginação (conjunto de IDs já delimitado) — nunca combine
    // fetch join de coleção com LIMIT/OFFSET na mesma query.
    @EntityGraph(attributePaths = {"tags", "contratante", "contratante.usuario", "fotos"})
    List<Vaga> findByIdIn(List<Long> ids);

    // RF05: uma única vaga detalhada pode carregar tags e dados públicos do
    // contratante juntos; fotos permanecem em consulta própria dentro da transação.
    @EntityGraph(attributePaths = {"tags", "contratante", "contratante.usuario"})
    @Query("select v from Vaga v where v.id = :id")
    Optional<Vaga> findDetalhesById(@Param("id") Long id);

    @Query(value = """
            select v.id as id,
                   count(distinct tv.tag_id) as quantidadeTagsCoincidentes
            from vagas v
            join tags_vaga tv on tv.vaga_id = v.id
            where v.status = 'aberta'
              and tv.tag_id in (:tagIds)
            group by v.id, v.data_publicacao
            order by count(distinct tv.tag_id) desc,
                     v.data_publicacao desc nulls last,
                     v.id desc
            """, countQuery = """
            select count(distinct v.id)
            from vagas v
            join tags_vaga tv on tv.vaga_id = v.id
            where v.status = 'aberta'
              and tv.tag_id in (:tagIds)
            """, nativeQuery = true)
    Page<VagaRecomendadaProjection> findRecomendadasPorTags(
            @Param("tagIds") Set<Long> tagIds,
            Pageable pageable);

    @Query("""
            select distinct tag.id
            from Vaga vaga
            join vaga.tags tag
            where vaga.contratante.usuarioId = :contratanteId
              and vaga.status in :statusAtivos
            """)
    Set<Long> findTagIdsDasVagasAtivasDoContratante(
            @Param("contratanteId") Long contratanteId,
            @Param("statusAtivos") Set<StatusVaga> statusAtivos);
}
