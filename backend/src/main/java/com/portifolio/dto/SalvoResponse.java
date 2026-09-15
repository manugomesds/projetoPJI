package com.portifolio.dto;

import com.portifolio.model.enums.TipoAlvoSalvo;
import java.time.LocalDateTime;
import java.util.List;

public record SalvoResponse(TipoAlvoSalvo tipoAlvo, Long alvoId, LocalDateTime dataSalvamento,
        String nome, String avatarUrl, String localizacao, String nomeContratante,
        String status, boolean disponivel, String href, String funcoes) {
    public record Estado(boolean salvo, Long quantidadeSalvos) {}
    public record Pagina(List<SalvoResponse> content, int page, int size, long totalElements,
                         int totalPages, boolean hasNext, boolean hasPrevious) {}
}
