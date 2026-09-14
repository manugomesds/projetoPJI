package com.portifolio.dto;

import java.time.LocalDateTime;

public record PortfolioArquivoResponse(Long id, String nomeOriginal, int tamanhoBytes, String tipoMime,
        LocalDateTime dataUpload, String tipo, String contentUrl) {}
