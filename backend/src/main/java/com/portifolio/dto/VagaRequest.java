package com.portifolio.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VagaRequest extends VagaAtualizacaoRequest {

    @NotNull(message = "Contratante é obrigatório")
    private Long contratanteId;
}
