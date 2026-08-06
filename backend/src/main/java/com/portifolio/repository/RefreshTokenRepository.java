package com.portifolio.repository;

import com.portifolio.model.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RF33 — Sessao persistente
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndAtivoTrue(String tokenHash);

    // Invalida todos os tokens ativos de um usuario (usado no logout total / exclusao de conta)
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.ativo = false WHERE rt.usuario.id = :usuarioId")
    void invalidarTodosPorUsuario(@Param("usuarioId") Long usuarioId);
}
