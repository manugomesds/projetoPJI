package com.portifolio.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Whitelist profissional RF13. Nunca serializa Usuario ou PerfilArtista. */
@Getter @Builder
public class TalentoResponse {
    private Long artistaId;
    private String nomeExibicao;
    private String avatarUrl;
    private String biografia;
    private String localizacao;
    private String urlPortfolio;
    private String tipoPerfilArtistico;
    private String raioAtuacao;
    private Boolean disponivelOportunidades;
    private List<Area> areas;
    private long quantidadeFuncoesCoincidentes;
    private long quantidadeEspecializacoesCoincidentes;
    private LocalDateTime ultimaAtualizacao;

    public record Item(Long id, String nome) {}
    public record Area(Short id, String nome, String nivelExperiencia, List<Item> funcoes, List<Item> especializacoes) {}
    public record Contexto(Long id, String titulo, Short areaId) {}
    public record Pagina<T>(List<T> content, int page, int size, long totalElements, boolean hasMore, Contexto contexto) {}
}
