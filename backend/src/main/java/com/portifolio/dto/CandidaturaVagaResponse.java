package com.portifolio.dto;

import com.portifolio.model.enums.StatusCandidatura;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CandidaturaVagaResponse {
    private Long candidaturaId;
    private Long artistaId;
    private String nomeArtista;
    private String biografia;
    private String localizacao;
    private String urlPortfolio;
    private String avatarUrl;
    private Set<Long> funcaoIds;
    private Set<Long> funcoesCoincidentes;
    private int quantidadeFuncoesCoincidentes;
    private String mensagemApresentacao;
    private String linkPortfolioCandidatura;
    private StatusCandidatura status;
    private LocalDateTime dataCandidatura;
}
