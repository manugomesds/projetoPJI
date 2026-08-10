package com.portifolio.controller;

import com.portifolio.dto.PerfilContratanteRequest;
import com.portifolio.dto.PerfilContratanteResponse;
import com.portifolio.service.PerfilContratanteService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfis-contratantes")
@RequiredArgsConstructor
public class PerfilContratanteController {

    private final PerfilContratanteService perfilContratanteService;

    @GetMapping
    public ResponseEntity<List<PerfilContratanteResponse>> listarTodos() {
        return ResponseEntity.ok(perfilContratanteService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PerfilContratanteResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(perfilContratanteService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<PerfilContratanteResponse> criar(@Valid @RequestBody PerfilContratanteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(perfilContratanteService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PerfilContratanteResponse> atualizar(@PathVariable Long id, @Valid @RequestBody PerfilContratanteRequest request) {
        return ResponseEntity.ok(perfilContratanteService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        perfilContratanteService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
