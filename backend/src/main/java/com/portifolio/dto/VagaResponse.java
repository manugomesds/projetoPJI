package com.portifolio.dto;

import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusVaga;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VagaResponse {
    private Long id;
    private Long contratanteId;
    private String titulo;
    private String descricao;
    private String requisitos;
    private BigDecimal remuneraValor;
    private String formaPagamento;
    private String cidade;
    private String estado;
    private String enderecoCompleto;
    private String beneficios;
    private ModeloTrabalho modeloTrabalho;
    private String tipoContrato;
    private StatusVaga status;
    private LocalDateTime dataPublicacao;
    private Set<Long> tagIds;

    // RF03 Fase 2 — true apenas dentro de vagasCanceladasComCandidatura;
    // sinaliza ao frontend exibir o badge "Vaga Cancelada"
    private boolean cancelada;
}
