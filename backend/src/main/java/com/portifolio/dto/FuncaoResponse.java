package com.portifolio.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FuncaoResponse {
    private Long id;
    private Short areaId;
    private String nome;
}
