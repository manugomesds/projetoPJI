package com.portifolio.scheduler;

import com.portifolio.service.VagaPrazoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.vagas.auto-close.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class VagaPrazoScheduler {

    private final VagaPrazoService vagaPrazoService;

    @Scheduled(
            fixedDelayString = "${app.vagas.auto-close.fixed-delay-ms:60000}",
            initialDelayString = "${app.vagas.auto-close.initial-delay-ms:60000}")
    public void encerrarVagasVencidas() {
        int encerradas = vagaPrazoService.encerrarVencidas();
        if (encerradas > 0) {
            log.info("Encerramento automático concluiu {} vaga(s) vencida(s).", encerradas);
        }
    }
}
