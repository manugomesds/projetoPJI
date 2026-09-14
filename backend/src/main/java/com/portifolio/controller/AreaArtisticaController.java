package com.portifolio.controller;

import com.portifolio.repository.AreaArtisticaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/areas")
@RequiredArgsConstructor
public class AreaArtisticaController {
    private final AreaArtisticaRepository areas;

    public record Area(Short id, String nome) {}

    @GetMapping
    public List<Area> listar() {
        return areas.findAll(Sort.by("id")).stream()
                .map(area -> new Area(area.getId(), area.getNome()))
                .toList();
    }
}
