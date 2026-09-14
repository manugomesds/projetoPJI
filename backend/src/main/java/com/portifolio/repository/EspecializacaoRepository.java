package com.portifolio.repository;

import com.portifolio.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EspecializacaoRepository extends JpaRepository<Especializacao, Long> {
    @org.springframework.data.jpa.repository.Query("select count(distinct e.id) from Funcao f join f.especializacoes e where f.area.id=:area and f.id in :funcoes and e.id in :ids")
    long contarCompativeis(Short area, java.util.Set<Long> funcoes, java.util.Set<Long> ids);

    @org.springframework.data.jpa.repository.Query("select distinct e from Funcao f join f.especializacoes e where f.area.id=:area and f.id in :funcoes order by e.id")
    org.springframework.data.domain.Page<Especializacao> buscarCompativeis(Short area, java.util.Set<Long> funcoes, org.springframework.data.domain.Pageable pageable);
}
