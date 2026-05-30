package com.portifolio.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PerfilContratanteResponse {
    private Long usuarioId;
    private String nomeEmpresa;
    private String biografia;
    private String localizacao;
    private String bannerUrl;
}
