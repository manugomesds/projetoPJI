package com.portifolio.controller;

import com.portifolio.dto.PerfilArtistaRequest;
import com.portifolio.dto.PerfilArtistaResponse;
import com.portifolio.service.PerfilArtistaService;
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
@RequestMapping("/api/perfis-artistas")
@RequiredArgsConstructor
public class PerfilArtistaController {

    private final PerfilArtistaService perfilArtistaService;

    @GetMapping
    public ResponseEntity<List<PerfilArtistaResponse>> listarTodos() {
        return ResponseEntity.ok(perfilArtistaService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PerfilArtistaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(perfilArtistaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<PerfilArtistaResponse> criar(@Valid @RequestBody PerfilArtistaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(perfilArtistaService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PerfilArtistaResponse> atualizar(@PathVariable Long id, @Valid @RequestBody PerfilArtistaRequest request) {
        return ResponseEntity.ok(perfilArtistaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        perfilArtistaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}