package com.portifolio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@JsonIgnoreProperties(value = {
        "usuarioId", "nivelMedalha", "scoreEngajamento", "perfilCompleto"
})
public class PerfilArtistaRequest {

    private String biografia;

    @Size(max = 150, message = "Localização deve ter no máximo 150 caracteres")
    private String localizacao;

    @Size(max = 255, message = "URL do portfólio deve ter no máximo 255 caracteres")
    @URL(message = "URL do portfólio deve ser válida")
    private String urlPortfolio;

    @Size(max = 255, message = "URL do banner deve ter no máximo 255 caracteres")
    @URL(message = "URL do banner deve ser válida")
    private String bannerUrl;

    private Set<@NotNull(message = "ID da tag não pode ser nulo") Long> tagIds;
}
