package com.portifolio.controller;

import com.portifolio.dto.FuncaoResponse;
import com.portifolio.service.FuncaoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/funcoes")
@RequiredArgsConstructor
public class FuncaoController {

    private final FuncaoService funcaoService;

    @GetMapping
    public ResponseEntity<List<FuncaoResponse>> listarTodos() {
        return ResponseEntity.ok(funcaoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FuncaoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(funcaoService.buscarPorId(id));
    }


}
