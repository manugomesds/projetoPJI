package com.portifolio.event;

import com.portifolio.realtime.NotificacaoRealtimeGateway;
import com.portifolio.service.NotificacaoPersistenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificacaoEventoListener {

    private final NotificacaoPersistenceService persistenceService;
    private final NotificacaoRealtimeGateway realtimeGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void processar(NotificacaoEvento evento) {
        List<NotificacaoPersistida> persistidas;
        try {
            persistidas = persistenceService.persistir(evento);
        } catch (RuntimeException erro) {
            log.error("Falha ao persistir notificacao pos-commit.", erro);
            return;
        }

        for (NotificacaoPersistida persistida : persistidas) {
            entregar(persistida);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void entregar(NotificacaoPersistida persistida) {
        try {
            realtimeGateway.entregar(
                    persistida.usuarioId(),
                    persistida.email(),
                    persistida.notificacao());
        } catch (RuntimeException erro) {
            log.warn("Falha isolada na entrega em tempo real da notificacao {}.",
                    persistida.notificacao().getId());
        }
    }
}
