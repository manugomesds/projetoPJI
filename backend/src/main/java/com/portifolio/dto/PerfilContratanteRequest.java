package com.portifolio.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PerfilContratanteRequest {

    @NotNull(message = "Usuário é obrigatório")
    private Long usuarioId;

    private String nomeEmpresa;
    private String biografia;
    private String localizacao;
    private String bannerUrl;
}
