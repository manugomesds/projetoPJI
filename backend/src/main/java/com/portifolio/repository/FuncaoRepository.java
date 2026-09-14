package com.portifolio.repository;

import com.portifolio.model.Funcao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuncaoRepository extends JpaRepository<Funcao, Long> {
    long countByAreaIdAndIdIn(Short areaId, java.util.Set<Long> ids);
    org.springframework.data.domain.Page<Funcao> findByAreaId(Short areaId, org.springframework.data.domain.Pageable pageable);
    List<Funcao> findByNomeContainingIgnoreCase(String nome);

}
