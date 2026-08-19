package com.portifolio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portifolio.model.enums.ModeloTrabalho;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VagaAtualizacaoRequest {

    @NotBlank(message = "Título é obrigatório")
    @Size(max = 150, message = "Título deve ter no máximo 150 caracteres")
    private String titulo;

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    @NotBlank(message = "Requisitos são obrigatórios")
    private String requisitos;

    @NotNull(message = "Remuneração é obrigatória")
    @DecimalMin(value = "0.00", message = "Remuneração não pode ser negativa")
    @Digits(integer = 8, fraction = 2, message = "Remuneração deve respeitar numeric(10,2)")
    private BigDecimal remuneraValor;

    @NotBlank(message = "Forma de pagamento é obrigatória")
    @Size(max = 100, message = "Forma de pagamento deve ter no máximo 100 caracteres")
    private String formaPagamento;

    @NotBlank(message = "Cidade é obrigatória")
    @Size(max = 100, message = "Cidade deve ter no máximo 100 caracteres")
    private String cidade;

    @NotBlank(message = "Estado é obrigatório")
    @Size(min = 2, max = 2, message = "Estado deve usar exatamente 2 caracteres")
    private String estado;

    private String enderecoCompleto;
    private String beneficios;

    @NotNull(message = "Modelo de trabalho é obrigatório")
    private ModeloTrabalho modeloTrabalho;

    @NotBlank(message = "Tipo de contrato é obrigatório")
    @Size(max = 100, message = "Tipo de contrato deve ter no máximo 100 caracteres")
    private String tipoContrato;

    private Set<@NotNull(message = "ID de tag não pode ser nulo") Long> tagIds;

    @Size(max = 100, message = "Categoria deve ter no máximo 100 caracteres")
    private String categoria;

    @Size(max = 100, message = "Experiência deve ter no máximo 100 caracteres")
    private String experiencia;

    private LocalDate dataLimiteCandidatura;

    @Size(max = 30, message = "Abrangência deve ter no máximo 30 caracteres")
    private String abrangencia;

    private List<@Size(max = 500, message = "URL da foto deve ter no máximo 500 caracteres") String> fotos;
}
