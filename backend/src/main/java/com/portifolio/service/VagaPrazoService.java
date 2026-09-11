package com.portifolio.service;

import com.portifolio.model.enums.StatusVaga;
import com.portifolio.repository.VagaRepository;
import com.portifolio.repository.projection.VagaPrazoProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VagaPrazoService {

    private static final int TAMANHO_MAXIMO_LOTE = 1000;
    private static final Set<StatusVaga> STATUS_ELEGIVEIS =
            Set.of(StatusVaga.ABERTA, StatusVaga.PAUSADA);

    private final VagaRepository vagaRepository;
    private final VagaPrazoPolicy vagaPrazoPolicy;
    private final VagaService vagaService;

    @Value("${app.vagas.auto-close.batch-size:100}")
    private int tamanhoLote;

    @Transactional
    public int encerrarVencidas() {
        LocalDate hoje = vagaPrazoPolicy.hoje();
        List<VagaPrazoProjection> elegiveis = vagaRepository.findElegiveisParaEncerramento(
                STATUS_ELEGIVEIS,
                hoje,
                PageRequest.of(0, Math.min(Math.max(1, tamanhoLote), TAMANHO_MAXIMO_LOTE)));
        int encerradas = 0;
        for (VagaPrazoProjection vaga : elegiveis) {
            int atualizadas = vagaRepository.encerrarSeElegivelEVencida(
                    vaga.getId(), hoje, STATUS_ELEGIVEIS, StatusVaga.ENCERRADA);
            if (atualizadas == 1) {
                vagaService.publicarEncerramentoAutomatico(vaga.getId(), vaga.getTitulo());
                encerradas++;
            }
        }
        return encerradas;
    }
}
