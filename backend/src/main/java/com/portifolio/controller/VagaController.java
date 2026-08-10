package com.portifolio.controller;

import com.portifolio.dto.VagaBuscaFiltro;
import com.portifolio.dto.VagaListagemResponse;
import com.portifolio.dto.VagaRequest;
import com.portifolio.dto.VagaResponse;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.service.VagaService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vagas")
@RequiredArgsConstructor
public class VagaController {

    private final VagaService vagaService;

    // RF03 — Listagem/busca pública, paginação cursor-based (RNF12)
    @GetMapping
    public ResponseEntity<VagaListagemResponse> listar(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) ModeloTrabalho modeloTrabalho,
            @RequestParam(required = false) String tipoContrato,
            @RequestParam(required = false) BigDecimal faixaSalarialMin,
            @RequestParam(required = false) BigDecimal faixaSalarialMax,
            @RequestParam(required = false) Set<Long> tagIds,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        VagaBuscaFiltro filtro = new VagaBuscaFiltro();
        filtro.setTitulo(titulo);
        filtro.setCidade(cidade);
        filtro.setEstado(estado);
        filtro.setModeloTrabalho(modeloTrabalho);
        filtro.setTipoContrato(tipoContrato);
        filtro.setFaixaSalarialMin(faixaSalarialMin);
        filtro.setFaixaSalarialMax(faixaSalarialMax);
        filtro.setTagIds(tagIds);
        filtro.setCursor(cursor);
        filtro.setSize(size);
        return ResponseEntity.ok(vagaService.listar(filtro));
    }

    @GetMapping("/minhas")
    public ResponseEntity<VagaListagemResponse> listarMinhas(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(vagaService.listarMinhas(cursor, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VagaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(vagaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<VagaResponse> criar(@Valid @RequestBody VagaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vagaService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VagaResponse> atualizar(@PathVariable Long id, @Valid @RequestBody VagaRequest request) {
        return ResponseEntity.ok(vagaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        vagaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
