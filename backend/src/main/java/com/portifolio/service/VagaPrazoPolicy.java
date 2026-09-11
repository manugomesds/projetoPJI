package com.portifolio.service;

import com.portifolio.model.Vaga;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VagaPrazoPolicy {

    private final Clock clock;

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public boolean estaVencida(Vaga vaga) {
        return estaVencida(vaga.getDataLimiteCandidatura());
    }

    public boolean estaVencida(LocalDate dataLimite) {
        return dataLimite != null && !dataLimite.isAfter(hoje());
    }
}
