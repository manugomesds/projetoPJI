package com.portifolio.dto;

import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ArtistaPublicoResponse implements PerfilPublicoResponse {
    private Long usuarioId;
    private String nomeExibicao;
    private String biografia;
    private String localizacao;
    private String urlPortfolio;
    private String bannerUrl;
    private String avatarUrl;
    private Set<FuncaoResponse> funcoes;
    private long quantidadeSalvos;
}
