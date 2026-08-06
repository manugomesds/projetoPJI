package com.portifolio.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

// RF03 / RNF12 — envelope de paginação cursor-based
@Getter
@Builder
public class VagaListagemResponse {
    private List<VagaResponse> content;
    private Long nextCursor;
    private boolean hasMore;

    // RF03 Fase 2 — vagas CANCELADA às quais o artista autenticado já se
    // candidatou. Vazia se ninguém estiver logado, se for CONTRATANTE,
    // ou se o artista não tiver candidaturas em vagas canceladas.
    private List<VagaResponse> vagasCanceladasComCandidatura;
}