package com.portifolio.repository;

import com.portifolio.model.Vaga;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VagaRepository extends JpaRepository<Vaga, Long>, JpaSpecificationExecutor<Vaga> {

    List<Vaga> findByTituloContainingIgnoreCase(String titulo);

    // RNF05: carrega tags (ManyToMany) e contratante junto, evitando N+1.
    // Usado APÓS a paginação (conjunto de IDs já delimitado) — nunca combine
    // fetch join de coleção com LIMIT/OFFSET na mesma query.
    @EntityGraph(attributePaths = {"tags", "contratante", "contratante.usuario", "fotos"})
    List<Vaga> findByIdIn(List<Long> ids);
}
