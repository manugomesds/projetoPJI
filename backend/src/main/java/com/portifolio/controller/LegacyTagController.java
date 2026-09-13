package com.portifolio.controller;

import com.portifolio.exception.UnprocessableEntityException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** IDs do catálogo removido não podem ser reinterpretados como IDs de funções. */
@RestController
public class LegacyTagController {
    @GetMapping({"/api/tags", "/api/tags/{id}"})
    public void catalogoRemovido() {
        throw new UnprocessableEntityException(
                "O catálogo de tags foi substituído pela taxonomia oficial. Consulte /api/funcoes.");
    }
}
