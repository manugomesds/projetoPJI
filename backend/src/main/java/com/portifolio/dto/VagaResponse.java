package com.portifolio.dto;

import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class VagaResponse {
    private Long id;
    private Long contratanteId;
    private String nomeContratante;
    private String titulo;
    private String descricao;
    private String requisitos;
    private BigDecimal remuneraValor;
    private BigDecimal valorMinimo;
    private BigDecimal valorMaximo;
    private Short areaId;
    private com.portifolio.model.enums.FormaRemuneracao formaRemuneracao;
    private String formaPagamento;
    private String cidade;
    private String estado;
    private String enderecoCompleto;
    private String beneficios;
    private ModeloTrabalho modeloTrabalho;
    private String tipoContrato;
    private StatusVaga status;
    private LocalDateTime dataPublicacao;
    private Set<Long> funcaoIds;
    private Set<Long> especializacaoIds;
    private Set<Integer> categoriaAfirmativaIds;
    private String categoria;
    private String experiencia;
    private LocalDate dataLimiteCandidatura;
    private String abrangencia;
    private List<String> fotos;
    private ContratantePublicoResponse contratantePublico;
    private Long minhaCandidaturaId;
    private StatusCandidatura statusMinhaCandidatura;

    // Calculado exclusivamente pela identidade autenticada e pelo vínculo
    // persistido; nunca por contratanteId fornecido pelo cliente.
    private boolean propriaDoContratante;

    // RF03 Fase 2 — true apenas dentro de vagasCanceladasComCandidatura;
    // sinaliza ao frontend exibir o badge "Vaga Cancelada"
    private boolean cancelada;
}
