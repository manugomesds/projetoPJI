package com.portifolio.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import java.math.BigDecimal;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PerfilArtistaRequest {

    @NotNull(message = "Usuário é obrigatório")
    private Long usuarioId;

    private String biografia;

    @Size(max = 150, message = "Localização deve ter no máximo 150 caracteres")
    private String localizacao;

    @Size(max = 255, message = "URL do portfólio deve ter no máximo 255 caracteres")
    @URL(message = "URL do portfólio deve ser válida")
    private String urlPortfolio;

    @Min(value = 1, message = "Nível da medalha deve ser no mínimo 1")
    @Max(value = 5, message = "Nível da medalha deve ser no máximo 5")
    private Integer nivelMedalha;

    @Digits(integer = 3, fraction = 2, message = "Score de engajamento deve respeitar numeric(5,2)")
    private BigDecimal scoreEngajamento;

    @Size(max = 255, message = "URL do banner deve ter no máximo 255 caracteres")
    @URL(message = "URL do banner deve ser válida")
    private String bannerUrl;
    @jakarta.validation.constraints.Positive
    private Short areaPrincipalId;
    private Set<@NotNull(message = "ID da funcao não pode ser nulo") Long> funcaoIds;
    private com.portifolio.model.enums.TipoPerfilArtistico tipoPerfilArtistico;
    private com.portifolio.model.enums.Abrangencia raioAtuacao;

    @com.fasterxml.jackson.annotation.JsonSetter("tagIds")
    public void rejeitarTagsLegadas(Object ignored) {
        throw new IllegalArgumentException("tagIds foi substituído por areaPrincipalId e funcaoIds.");
    }
}
