package com.portifolio.dto;

import com.portifolio.model.enums.ModeloTrabalho;
import java.math.BigDecimal;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

// RF03 — parâmetros de busca/listagem de vagas (todos opcionais, exceto defaults de paginação)
@Getter
@Setter
public class VagaBuscaFiltro {
    private String titulo;
    private String empresa;
    private String cidade;
    private String estado;
    private ModeloTrabalho modeloTrabalho;
    private String tipoContrato;
    private BigDecimal faixaSalarialMin;
    private BigDecimal faixaSalarialMax;
    private String areaAtuacao;
    private Set<Long> funcaoIds;
    private Long cursor;
    private Long cursorCanceladas;
    private Integer size;
}
