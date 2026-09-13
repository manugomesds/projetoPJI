package com.portifolio.service;

import com.portifolio.dto.FuncaoResponse;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.model.Funcao;
import com.portifolio.repository.FuncaoRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FuncaoService {

    private final FuncaoRepository funcaoRepository;

    @Transactional(readOnly = true)
    public List<FuncaoResponse> listarTodos() {
        return funcaoRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FuncaoResponse buscarPorId(Long id) {
        Funcao funcao = funcaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Funcao não encontrada."));
        return toResponse(funcao);
    }

    private FuncaoResponse toResponse(Funcao funcao) {
        return FuncaoResponse.builder()
                .id(funcao.getId())
                .areaId(funcao.getArea().getId())
                .nome(funcao.getNome())
                .build();
    }
}
