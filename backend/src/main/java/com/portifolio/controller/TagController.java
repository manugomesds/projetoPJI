package com.portifolio.controller;

import com.portifolio.dto.TagRequest;
import com.portifolio.dto.TagResponse;
import com.portifolio.service.TagService;
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
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagResponse>> listarTodos() {
        return ResponseEntity.ok(tagService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<TagResponse> criar(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagResponse> atualizar(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(tagService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        tagService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
