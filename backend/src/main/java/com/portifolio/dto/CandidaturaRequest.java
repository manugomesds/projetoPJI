package com.portifolio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@JsonIgnoreProperties(value = {"artistaId", "status"})
public class CandidaturaRequest {

    @NotNull(message = "Vaga é obrigatória")
    private Long vagaId;

    @NotBlank(message = "Mensagem de apresentação é obrigatória")
    @Size(max = 2000, message = "Mensagem de apresentação deve ter no máximo 2000 caracteres")
    private String mensagemApresentacao;

    @NotBlank(message = "Link do portfólio é obrigatório")
    @Size(max = 255, message = "Link do portfólio deve ter no máximo 255 caracteres")
    @URL(message = "Link do portfólio deve ser uma URL válida")
    private String linkPortfolioCandidatura;
}
