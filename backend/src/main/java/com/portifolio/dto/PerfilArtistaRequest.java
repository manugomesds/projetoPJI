package com.portifolio.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PerfilArtistaRequest {

    @NotNull(message = "Usuário é obrigatório")
    private Long usuarioId;

    private String biografia;
    private String localizacao;
    private String urlPortfolio;
    private Integer nivelMedalha;
    private BigDecimal scoreEngajamento;
    private String bannerUrl;
    private Set<Long> tagIds;
}
