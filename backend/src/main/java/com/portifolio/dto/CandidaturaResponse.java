package com.portifolio.dto;

import com.portifolio.model.enums.StatusCandidatura;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CandidaturaResponse {
    private Long id;
    private Long vagaId;
    private Long artistaId;
    private String mensagemApresentacao;
    private String linkPortfolioCandidatura;
    private StatusCandidatura status;
    private LocalDateTime dataCandidatura;
}
