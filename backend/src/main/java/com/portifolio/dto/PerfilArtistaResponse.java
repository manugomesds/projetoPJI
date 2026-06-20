package com.portifolio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PerfilArtistaResponse {
    private Long usuarioId;
    private String biografia;
    private String localizacao;
    private String urlPortfolio;
    private Integer nivelMedalha;
    private BigDecimal scoreEngajamento;
    private String bannerUrl;
    private LocalDateTime ultimaAtualizacao;
    private Set<Long> tagIds;

    // RF34: URL do avatar resolvida (foto propria > foto Google > DiceBear)
    private String avatarUrl;
}
