package com.portifolio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portifolio.model.enums.StatusCandidatura;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(value = {
        "id", "vagaId", "artistaId", "mensagemApresentacao",
        "linkPortfolioCandidatura", "dataCandidatura"
})
public class CandidaturaStatusRequest {

    @NotNull(message = "Novo status é obrigatório")
    private StatusCandidatura status;
}
