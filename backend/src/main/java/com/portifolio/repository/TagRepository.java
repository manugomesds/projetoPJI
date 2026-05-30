package com.portifolio.repository;

import com.portifolio.model.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByNomeContainingIgnoreCase(String nome);
    Optional<Tag> findByNomeIgnoreCase(String nome);
}
