package com.portifolio.service.google;

import jakarta.persistence.EntityManager;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GoogleLinkLock {

    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquear(String email, String googleId) {
        Stream.of("email:" + email, "google:" + googleId)
                .sorted()
                .forEach(this::bloquearChave);
    }

    private void bloquearChave(String chave) {
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtextextended(cast(:chave as text), 0))")
                .setParameter("chave", chave)
                .getSingleResult();
    }
}
