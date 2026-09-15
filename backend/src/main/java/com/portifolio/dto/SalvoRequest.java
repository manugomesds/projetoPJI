package com.portifolio.dto;

import com.portifolio.model.enums.TipoAlvoSalvo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SalvoRequest(@NotNull TipoAlvoSalvo tipoAlvo, @NotNull @Positive Long alvoId) {}
