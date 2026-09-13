package com.portifolio.repository;

import com.portifolio.model.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Override
    @EntityGraph(attributePaths = "responsavelLegal")
    Optional<Usuario> findById(Long id);

    @EntityGraph(attributePaths = "responsavelLegal")
    Optional<Usuario> findByEmail(String email);

    @EntityGraph(attributePaths = "responsavelLegal")
    Optional<Usuario> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "responsavelLegal")
    Optional<Usuario> findByTokenRecuperacao(String tokenRecuperacao);

    // RF32: busca por conta Google vinculada
    @EntityGraph(attributePaths = "responsavelLegal")
    Optional<Usuario> findByGoogleId(String googleId);
}
