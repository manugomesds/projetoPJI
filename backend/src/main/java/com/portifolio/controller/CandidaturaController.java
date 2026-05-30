package com.portifolio.controller;

import com.portifolio.dto.CandidaturaRequest;
import com.portifolio.dto.CandidaturaResponse;
import com.portifolio.service.CandidaturaService;
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
@RequestMapping("/api/candidaturas")
@RequiredArgsConstructor
public class CandidaturaController {

    private final CandidaturaService candidaturaService;

    @GetMapping
    public ResponseEntity<List<CandidaturaResponse>> listarTodos() {
        return ResponseEntity.ok(candidaturaService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CandidaturaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(candidaturaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<CandidaturaResponse> criar(@Valid @RequestBody CandidaturaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(candidaturaService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CandidaturaResponse> atualizar(@PathVariable Long id, @Valid @RequestBody CandidaturaRequest request) {
        return ResponseEntity.ok(candidaturaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        candidaturaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
