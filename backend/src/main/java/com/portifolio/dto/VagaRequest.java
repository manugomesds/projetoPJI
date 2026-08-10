package com.portifolio.dto;

import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusVaga;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VagaRequest {

    @NotNull(message = "Contratante é obrigatório")
    private Long contratanteId;

    @NotBlank(message = "Título é obrigatório")
    private String titulo;

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    @NotBlank(message = "Requisitos são obrigatórios")
    private String requisitos;

    @NotNull(message = "Remuneração é obrigatória")
    private BigDecimal remuneraValor;

    @NotBlank(message = "Forma de pagamento é obrigatória")
    private String formaPagamento;

    @NotBlank(message = "Cidade é obrigatória")
    private String cidade;

    @NotBlank(message = "Estado é obrigatório")
    private String estado;

    private String enderecoCompleto;
    private String beneficios;

    @NotNull(message = "Modelo de trabalho é obrigatório")
    private ModeloTrabalho modeloTrabalho;

    @NotBlank(message = "Tipo de contrato é obrigatório")
    private String tipoContrato;

    private StatusVaga status;
    private Set<Long> tagIds;

    private String categoria;
    private String experiencia;
    private LocalDate dataLimiteCandidatura;
    private String abrangencia;
    private List<String> fotos;
}
