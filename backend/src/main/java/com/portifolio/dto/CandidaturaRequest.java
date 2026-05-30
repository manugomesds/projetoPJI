package com.portifolio.dto;

import com.portifolio.model.enums.StatusCandidatura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandidaturaRequest {

    @NotNull(message = "Vaga é obrigatória")
    private Long vagaId;

    @NotNull(message = "Artista é obrigatório")
    private Long artistaId;

    @NotBlank(message = "Mensagem de apresentação é obrigatória")
    private String mensagemApresentacao;

    @NotBlank(message = "Link do portfólio é obrigatório")
    private String linkPortfolioCandidatura;

    private StatusCandidatura status;
}
