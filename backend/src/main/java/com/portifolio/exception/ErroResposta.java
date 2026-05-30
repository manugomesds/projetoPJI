package com.portifolio.exception;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErroResposta {
    private LocalDateTime timestamp;
    private int status;
    private String mensagem;
    private List<String> detalhes;
}
