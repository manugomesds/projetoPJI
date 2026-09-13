package com.portifolio.dto;

import com.portifolio.model.enums.ModeloTrabalho;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardVagaResponse {
    private Long id;
    private String titulo;
    private String nomeContratante;
    private BigDecimal remuneraValor;
    private String cidade;
    private String estado;
    private ModeloTrabalho modeloTrabalho;
    private LocalDateTime dataPublicacao;
    private Set<FuncaoResponse> funcoes;
    private long quantidadeFuncoesCoincidentes;
}
