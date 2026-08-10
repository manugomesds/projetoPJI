package com.portifolio.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PerfilContratanteResponse {
    private Long usuarioId;
    private String nomeEmpresa;
    private String tipoPerfil;
    private String biografia;
    private String localizacao;
    private String bannerUrl;

    // RF34: URL do avatar resolvida (foto propria > foto Google > DiceBear)
    private String avatarUrl;
}
