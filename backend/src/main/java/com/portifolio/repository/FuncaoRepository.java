package com.portifolio.repository;

import com.portifolio.model.Funcao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuncaoRepository extends JpaRepository<Funcao, Long> {
    List<Funcao> findByNomeContainingIgnoreCase(String nome);

}
