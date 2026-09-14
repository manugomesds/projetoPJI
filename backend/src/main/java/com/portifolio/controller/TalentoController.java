package com.portifolio.controller;

import com.portifolio.dto.*;
import com.portifolio.dto.TalentoResponse.*;
import com.portifolio.model.enums.*;
import com.portifolio.service.TalentoService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/talentos")
@RequiredArgsConstructor
public class TalentoController {
    private final TalentoService service;

    @GetMapping
    public Pagina<TalentoResponse> buscar(
            @RequestParam(required=false) Short areaId,
            @RequestParam(required=false) Set<Long> funcaoIds,
            @RequestParam(required=false) Set<Long> especializacaoIds,
            @RequestParam(required=false) String localizacao,
            @RequestParam(required=false) Set<Abrangencia> raios,
            @RequestParam(required=false) NivelExperiencia experienciaMinima,
            @RequestParam(required=false) Boolean disponivel,
            @RequestParam(required=false) Set<TipoPerfilArtistico> tipos,
            @RequestParam(required=false) Long vagaId,
            @RequestParam(defaultValue="false") boolean recomendados,
            @RequestParam(defaultValue="RELEVANCIA") FiltroTalentos.Ordenacao ordenacao,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String cidade,
            @RequestParam(required=false) String estado) {
        if (cidade != null || estado != null)
            throw new com.portifolio.exception.UnprocessableEntityException("Cidade/Estado estruturados indisponíveis. Use localização textual.");
        return service.buscar(new FiltroTalentos(areaId, funcaoIds, especializacaoIds, localizacao,
                raios, experienciaMinima, disponivel, tipos, vagaId, recomendados, ordenacao, page, size));
    }

    @GetMapping("/contextos")
    public Pagina<Contexto> contextos(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.contextos(page, size);
    }
    @GetMapping("/areas")
    public Pagina<Item> areas(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.areas(page, size);
    }
    @GetMapping("/funcoes")
    public Pagina<Item> funcoes(@RequestParam Short areaId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return service.funcoes(areaId, page, size);
    }
    @GetMapping("/especializacoes")
    public Pagina<Item> especializacoes(@RequestParam Short areaId, @RequestParam Set<Long> funcaoIds,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.especializacoes(areaId, funcaoIds, page, size);
    }
}
