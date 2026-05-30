package com.portifolio.repository;

import com.portifolio.model.Vaga;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VagaRepository extends JpaRepository<Vaga, Long> {
    List<Vaga> findByTituloContainingIgnoreCase(String titulo);
}
