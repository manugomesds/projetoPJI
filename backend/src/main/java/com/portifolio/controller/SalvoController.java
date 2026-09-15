package com.portifolio.controller;

import com.portifolio.dto.*;
import com.portifolio.model.enums.TipoAlvoSalvo;
import com.portifolio.service.SalvoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/salvos") @RequiredArgsConstructor
public class SalvoController {
    private final SalvoService service;
    @PostMapping public ResponseEntity<SalvoResponse.Estado> salvar(@Valid @RequestBody SalvoRequest request) {
        var result=service.salvar(request);
        return ResponseEntity.status(result.criado() ? 201 : 200).body(result.estado());
    }
    @DeleteMapping("/{tipoAlvo}/{alvoId}")
    public ResponseEntity<Void> remover(@PathVariable TipoAlvoSalvo tipoAlvo,@PathVariable Long alvoId) {
        service.remover(tipoAlvo,alvoId); return ResponseEntity.noContent().build();
    }
    @GetMapping("/estado") public SalvoResponse.Estado estado(@RequestParam TipoAlvoSalvo tipoAlvo,@RequestParam Long alvoId) {
        return service.estado(tipoAlvo,alvoId);
    }
    @GetMapping public SalvoResponse.Pagina listar(@RequestParam(required=false) TipoAlvoSalvo tipoAlvo,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.listar(tipoAlvo,page,size);
    }
}
